#!/usr/bin/env python3
"""Detached deployment worker. Only accepts a fixed release tag and allowlisted operations."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import time
import uuid
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(os.environ.get("ATLAS_INSTALL_ROOT", "/opt/atlas")).resolve()
ENV_FILE = Path(os.environ.get("ATLAS_ENV_FILE", str(ROOT / ".env"))).resolve()
RELEASE_DIR = Path(os.environ.get("ATLAS_RELEASE_DIR", str(ROOT / "releases"))).resolve()
REPO_URL = os.environ.get("ATLAS_REPO_URL", "https://github.com/wwxst/atlas-console.git")
API = os.environ.get("ATLAS_GITHUB_API", "https://api.github.com/repos/wwxst/atlas-console")
TAG_RE = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
RELEASE_ID_RE = re.compile(r"^[0-9a-f]{40}_[0-9a-f]{40}$")
STATE_FILE = RELEASE_DIR / "updater-status.json"


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    os.chmod(temp, 0o600)
    os.replace(temp, path)


def read_json(path: Path, fallback=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return fallback


def request_json(url: str):
    request = Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "atlas-console-customer-updater",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    with urlopen(request, timeout=15) as response:
        return json.loads(response.read(2 * 1024 * 1024).decode("utf-8"))


def release_for_tag(tag: str):
    if not TAG_RE.fullmatch(tag):
        raise ValueError("Invalid release tag")
    release = request_json(API + "/releases/tags/" + tag)
    if release.get("draft") or release.get("prerelease") or release.get("tag_name") != tag:
        raise ValueError("Requested tag is not a published stable release")
    return release


def manifest_id():
    try:
        lines = (RELEASE_DIR / "current.env").read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    if len(lines) != 2:
        return None
    front = lines[0].removeprefix("frontend=git-")
    back = lines[1].removeprefix("backend=git-")
    if lines[0] != "frontend=git-" + front or lines[1] != "backend=git-" + back:
        return None
    if not SHA_RE.fullmatch(front) or not SHA_RE.fullmatch(back):
        return None
    return front + "_" + back


def update_state(request_id: str, **changes):
    state = read_json(STATE_FILE, {})
    if not isinstance(state, dict) or state.get("requestId") != request_id:
        state = {"requestId": request_id}
    state.update(changes)
    write_json(STATE_FILE, state)


def run():
    parser = argparse.ArgumentParser()
    parser.add_argument("--operation", required=True, choices=("deploy", "rollback", "rollback-history"))
    parser.add_argument("--request-id", required=True)
    parser.add_argument("--tag")
    parser.add_argument("--release-id")
    args = parser.parse_args()

    request_id = args.request_id
    if not re.fullmatch(r"[0-9a-f-]{36}", request_id):
        raise SystemExit("Invalid request ID")
    if args.operation == "deploy" and (not args.tag or not TAG_RE.fullmatch(args.tag)):
        raise SystemExit("Invalid release tag")
    if args.operation == "rollback-history" and (not args.release_id or not RELEASE_ID_RE.fullmatch(args.release_id)):
        raise SystemExit("Invalid historical release ID")

    log_dir = RELEASE_DIR / "update-logs"
    work_root = ROOT / "update-workdir"
    log_dir.mkdir(parents=True, exist_ok=True)
    work_root.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / (request_id + ".log")
    stage = work_root / request_id
    payload = read_json(STATE_FILE, {})
    payload.update({"state": "in_progress", "conclusion": None, "message": "正在下载并验证正式版本",
                    "logFile": str(log_path)})
    write_json(STATE_FILE, payload)

    try:
        if args.operation != "deploy":
            # Application rollback is an offline operation: the required Docker
            # images and signed-in history are already present on this server.
            # Never require GitHub to be reachable to recover a failed release.
            source = ROOT / "atlas-console"
            script = source / "deploy" / "release.sh"
            if not script.is_file():
                raise RuntimeError("本机缺少回滚执行脚本")
            command = ["bash", str(script), args.operation]
            if args.operation == "rollback-history":
                command.append(args.release_id)
            update_state(request_id, message="正在恢复上一套应用镜像")
            with log_path.open("w", encoding="utf-8") as log:
                result = subprocess.run(command, cwd=source, env=os.environ.copy(),
                                        stdout=log, stderr=subprocess.STDOUT,
                                        timeout=900, check=False)
            if result.returncode != 0:
                raise RuntimeError("回滚失败，请查看更新日志")
            current_id = manifest_id()
            current_meta = read_json(RELEASE_DIR / "versions" / (current_id + ".json"), {}) if current_id else {}
            update_state(request_id, state="completed", conclusion="success",
                         message="回滚成功", currentVersion=current_meta.get("version"),
                         finishedAt=time.time())
            return

        if stage.exists():
            shutil.rmtree(stage)
        tag = args.tag
        release = release_for_tag(tag)
        command = ["git", "clone", "--depth", "1", "--single-branch", "--branch", tag, REPO_URL, str(stage)]
        with log_path.open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=180, check=False)
            if result.returncode != 0:
                raise RuntimeError("下载 GitHub Release 源码失败")
            sha = subprocess.run(["git", "-c", "safe.directory=" + str(stage), "-C", str(stage),
                                  "rev-parse", "HEAD"], capture_output=True, text=True, timeout=10, check=True).stdout.strip()
            if not SHA_RE.fullmatch(sha):
                raise RuntimeError("Release commit SHA 无效")
            package = read_json(stage / "frontend" / "package.json", {})
            package_version = package.get("version") if isinstance(package, dict) else None
            if package_version != tag[1:]:
                raise RuntimeError("GitHub Release 标签与前端 package.json 版本不一致")
            update_state(request_id, releaseVersion=package_version, releaseTag=tag,
                         releaseUrl=release.get("html_url"), message="正在构建前后端并执行健康检查")
            env = os.environ.copy()
            env.update({
                "ATLAS_ENV_FILE": str(ENV_FILE),
                "ATLAS_RELEASE_DIR": str(RELEASE_DIR),
                "ATLAS_INSTALL_ROOT": str(ROOT),
                "VERIFIED_FRONTEND_SHA": sha,
                "VERIFIED_BACKEND_SHA": sha,
            })
            operation = ["bash", str(stage / "deploy" / "release.sh"), "deploy"]
            result = subprocess.run(operation, cwd=stage, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=2400, check=False)
            if result.returncode != 0:
                raise RuntimeError("部署命令失败，请查看更新日志")
        release_id = sha + "_" + sha
        write_json(RELEASE_DIR / "versions" / (release_id + ".json"), {
            "version": package_version, "tag": tag,
            "releaseUrl": release.get("html_url"),
            "publishedAt": release.get("published_at"),
        })
        update_state(request_id, state="completed", conclusion="success",
                     message="更新成功", currentVersion=package_version,
                     finishedAt=time.time(), releaseVersion=package_version,
                     releaseTag=tag, releaseUrl=release.get("html_url"))
    except Exception as exc:
        # Do not expose subprocess command lines or credentials in the UI.
        update_state(request_id, state="completed", conclusion="failure",
                     message=str(exc)[:240], finishedAt=time.time())
        raise
    finally:
        if stage.exists():
            shutil.rmtree(stage, ignore_errors=True)


if __name__ == "__main__":
    run()
