#!/usr/bin/env python3
"""Native BaoTa release worker. No Docker, shell commands, or unverified source builds."""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
import re
import shutil
import subprocess
import tarfile
import time
import uuid
from pathlib import Path, PurePosixPath
from urllib.request import Request, urlopen

import server
import worker

ROOT = Path(os.environ.get("ATLAS_INSTALL_ROOT", "/opt/atlas-native")).resolve()
RELEASE_DIR = Path(os.environ.get("ATLAS_RELEASE_DIR", str(ROOT / "releases"))).resolve()
CURRENT = ROOT / "runtime" / "current"
APPS = RELEASE_DIR / "apps"
SERVICE = "atlas-native-backend.service"
ASSETS = ("atlas-frontend.tar.gz", "atlas-backend.jar")
MAX_BYTES = {"atlas-frontend.tar.gz": 100 * 1024 * 1024,
             "atlas-backend.jar": 250 * 1024 * 1024,
             "SHA256SUMS": 2048}
TAG = re.compile(r"^v(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)$")
SHA = re.compile(r"^[a-f0-9]{40}$")
RELEASE_ID = re.compile(r"^([a-f0-9]{40})_\1$")
HEALTH_URL = "http://127.0.0.1:8091/api/sys-user/auth/me"


def manifest(release_id: str) -> str:
    if not RELEASE_ID.fullmatch(release_id):
        raise ValueError("无效的发布版本标识")
    sha = release_id[:40]
    return f"frontend=git-{sha}\nbackend=git-{sha}\n"


def installed_id(path: Path) -> str | None:
    found = server.read_manifest(path)
    return found if found and RELEASE_ID.fullmatch(found) else None


def atomic_bytes(path: Path, content: bytes):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temp.write_bytes(content)
    os.chmod(temp, 0o644)
    os.replace(temp, path)


def download(tag: str, asset: str, destination: Path):
    if not TAG.fullmatch(tag) or asset not in (*ASSETS, "SHA256SUMS"):
        raise ValueError("非法的发布资产")
    url = f"https://github.com/wwxst/atlas-console/releases/download/{tag}/{asset}"
    request = Request(url, headers={"User-Agent": "atlas-native-updater"})
    limit = MAX_BYTES[asset]
    with urlopen(request, timeout=30) as response, destination.open("wb") as output:
        total = 0
        while chunk := response.read(65536):
            total += len(chunk)
            if total > limit:
                raise ValueError("发布资产超过安全大小限制")
            output.write(chunk)
    if not total:
        raise ValueError("发布资产为空")


def verify_checksums(directory: Path):
    lines = (directory / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    pattern = re.compile(r"^([a-f0-9]{64})  (atlas-frontend\.tar\.gz|atlas-backend\.jar)$")
    expected = {}
    for line in lines:
        match = pattern.fullmatch(line)
        if not match or match.group(2) in expected:
            raise ValueError("发布校验清单不合法")
        expected[match.group(2)] = match.group(1)
    if set(expected) != set(ASSETS):
        raise ValueError("发布资产校验清单不完整")
    for name in ASSETS:
        with (directory / name).open("rb") as stream:
            sha = hashlib.file_digest(stream, "sha256").hexdigest()
        if sha != expected[name]:
            raise ValueError("发布资产 SHA256 校验失败")


def safe_extract_frontend(archive: Path, destination: Path):
    destination.mkdir(parents=True, exist_ok=False)
    total = 0
    seen = set()
    with tarfile.open(archive, "r:gz") as tar:
        members = tar.getmembers()
        if len(members) > 5000:
            raise ValueError("前端资产文件数量超过限制")
        for member in members:
            name = PurePosixPath(member.name)
            if (name.is_absolute() or any(part in ("", "..") for part in name.parts)
                    or member.issym() or member.islnk() or not (member.isfile() or member.isdir())):
                raise ValueError("前端发布包包含不安全路径或文件类型")
            relative = Path(*name.parts)
            if relative in seen:
                raise ValueError("前端发布包存在重复文件名")
            seen.add(relative)
            target = destination / relative
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            total += member.size
            if member.size > 30 * 1024 * 1024 or total > 250 * 1024 * 1024:
                raise ValueError("前端发布内容超出限制")
            target.parent.mkdir(parents=True, exist_ok=True)
            stream = tar.extractfile(member)
            if stream is None:
                raise ValueError("发布包内容读取失败")
            with stream, target.open("xb") as out:
                shutil.copyfileobj(stream, out)
            target.chmod(0o644)
    if not (destination / "index.html").is_file():
        raise ValueError("前端发布包缺少 index.html")


def get_commit(tag: str) -> str:
    result = worker.request_json(worker.API + "/commits/" + tag)
    sha = result.get("sha")
    if not isinstance(sha, str) or not SHA.fullmatch(sha):
        raise ValueError("无法验证 GitHub Release 提交")
    return sha


def restart_and_check():
    subprocess.run(["sudo", "-n", "/usr/bin/systemctl", "restart", SERVICE],
                   check=True, timeout=30)
    for _ in range(30):
        try:
            request = Request(HEALTH_URL, headers={"User-Agent": "atlas-native-health"})
            try:
                with urlopen(request, timeout=2) as response:
                    code = response.status
            except __import__("urllib.error", fromlist=["HTTPError"]).HTTPError as exc:
                code = exc.code
            if code == 401:
                return
        except OSError:
            pass
        time.sleep(2)
    raise RuntimeError("JAR 服务健康检查失败")


def activate(release_id: str, version: str | None = None, tag: str | None = None,
             release_url: str | None = None):
    target = APPS / release_id
    if not target.is_dir() or not (target / "backend.jar").is_file() or not (target / "frontend" / "index.html").is_file():
        raise ValueError("本地版本文件不存在或不完整")
    previous_id = installed_id(RELEASE_DIR / "current.env")
    if previous_id == release_id:
        raise ValueError("当前已是目标版本")
    previous_target = CURRENT.resolve() if CURRENT.is_symlink() else None
    if CURRENT.exists() and not CURRENT.is_symlink():
        raise RuntimeError("当前运行目录不是受管理的符号链接，拒绝覆盖")
    temp_link = ROOT / ("current." + uuid.uuid4().hex)
    temp_link.symlink_to(target, target_is_directory=True)
    os.replace(temp_link, CURRENT)
    try:
        restart_and_check()
    except Exception:
        if previous_target is not None and previous_target.is_dir():
            restore = ROOT / ("current." + uuid.uuid4().hex)
            restore.symlink_to(previous_target, target_is_directory=True)
            os.replace(restore, CURRENT)
            try:
                restart_and_check()
            except Exception as recovery_error:
                raise RuntimeError("新版健康检查失败，旧版服务也未能恢复，需人工处理") from recovery_error
        else:
            CURRENT.unlink(missing_ok=True)
        raise RuntimeError("新版未通过健康检查，应用文件已恢复到旧版")
    history = RELEASE_DIR / "history"
    history.mkdir(parents=True, exist_ok=True)
    history.chmod(0o755)
    if previous_id:
        old_bytes = manifest(previous_id).encode()
        atomic_bytes(history / (previous_id + ".env"), old_bytes)
        atomic_bytes(RELEASE_DIR / "previous.env", old_bytes)
    atomic_bytes(RELEASE_DIR / "current.env", manifest(release_id).encode())
    atomic_bytes(history / (release_id + ".env"), manifest(release_id).encode())
    if version is not None:
        versions = RELEASE_DIR / "versions"
        versions.mkdir(parents=True, exist_ok=True)
        versions.chmod(0o755)
        atomic_bytes(versions / (release_id + ".json"),
                     json.dumps({"version": version, "tag": tag, "releaseUrl": release_url},
                                ensure_ascii=False).encode("utf-8"))


def deploy(tag: str):
    if not TAG.fullmatch(tag):
        raise ValueError("只能更新到正式 SemVer 版本")
    release = worker.release_for_tag(tag)
    assets = {item.get("name"): item.get("browser_download_url") for item in release.get("assets", [])}
    for name in (*ASSETS, "SHA256SUMS"):
        expected_url = f"https://github.com/wwxst/atlas-console/releases/download/{tag}/{name}"
        if assets.get(name) != expected_url:
            raise RuntimeError("GitHub Release 缺少可信的原生部署资产")
    sha = get_commit(tag)
    release_id = sha + "_" + sha
    directory = RELEASE_DIR / "incoming" / uuid.uuid4().hex
    directory.mkdir(parents=True)
    try:
        for name in (*ASSETS, "SHA256SUMS"):
            download(tag, name, directory / name)
        verify_checksums(directory)
        app = APPS / release_id
        if not app.exists():
            staged = APPS / ("staging-" + uuid.uuid4().hex)
            staged.mkdir(parents=True)
            staged.chmod(0o755)  # Nginx and the unprivileged Java service must traverse this directory.
            try:
                safe_extract_frontend(directory / "atlas-frontend.tar.gz", staged / "frontend")
                shutil.copyfile(directory / "atlas-backend.jar", staged / "backend.jar")
                (staged / "backend.jar").chmod(0o644)
                for folder in staged.rglob("*"):
                    if folder.is_dir():
                        folder.chmod(0o755)
                staged.rename(app)
            except Exception:
                shutil.rmtree(staged, ignore_errors=True)
                raise
        activate(release_id, tag[1:], tag, release.get("html_url"))
    finally:
        shutil.rmtree(directory, ignore_errors=True)


def rollback(release_id: str):
    source = RELEASE_DIR / "history" / (release_id + ".env")
    if installed_id(source) != release_id:
        raise ValueError("历史版本未经验证")
    activate(release_id)


def run(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation", required=True, choices=("deploy", "rollback", "rollback-history"))
    parser.add_argument("--tag")
    parser.add_argument("--release-id")
    parser.add_argument("--request-id", required=True)
    args = parser.parse_args(argv)
    if not re.fullmatch(r"[0-9a-f-]{36}", args.request_id):
        raise ValueError("更新请求标识不合法")
    RELEASE_DIR.mkdir(parents=True, exist_ok=True)
    APPS.mkdir(parents=True, exist_ok=True)
    APPS.chmod(0o755)
    lock = RELEASE_DIR / ".native-update.lock"
    with lock.open("a+b") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            server.write_json(RELEASE_DIR / "updater-status.json", {
                "requestId": args.request_id,
                "state": "in_progress", "conclusion": None,
                "operation": args.operation, "message": "正在验证正式发布包",
                "workerPid": os.getpid(), "startedAt": time.time(),
            })
            if args.operation == "deploy":
                tag = args.tag
                if tag is None:
                    latest = worker.request_json(worker.API + "/releases/latest")
                    tag = latest.get("tag_name")
                deploy(tag)
            elif args.operation == "rollback":
                release_id = installed_id(RELEASE_DIR / "previous.env")
                if release_id is None:
                    raise ValueError("没有可回滚的上一版本")
                rollback(release_id)
            else:
                if args.release_id is None or RELEASE_ID.fullmatch(args.release_id) is None:
                    raise ValueError("历史版本标识无效")
                rollback(args.release_id)
            payload = server.read_json(RELEASE_DIR / "updater-status.json", {})
            payload.update({"state": "completed", "conclusion": "success",
                            "message": "操作成功", "finishedAt": time.time(),
                            "currentVersion": server.current_version()})
            server.write_json(RELEASE_DIR / "updater-status.json", payload)
        except Exception as exc:
            payload = server.read_json(RELEASE_DIR / "updater-status.json", {})
            payload.update({"state": "completed", "conclusion": "failure",
                            "message": str(exc)[:160], "finishedAt": time.time()})
            server.write_json(RELEASE_DIR / "updater-status.json", payload)
            raise


if __name__ == "__main__":
    run()
