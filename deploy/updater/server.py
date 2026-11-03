#!/usr/bin/env python3
"""Private, token-authenticated update API. It only launches a fixed worker image."""
from __future__ import annotations

import hmac
import json
import os
import re
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import URLError, HTTPError
from urllib.request import Request, urlopen

ROOT = Path(os.environ.get("ATLAS_INSTALL_ROOT", "/opt/atlas")).resolve()
ENV_FILE = Path(os.environ.get("ATLAS_ENV_FILE", str(ROOT / ".env"))).resolve()
RELEASE_DIR = Path(os.environ.get("ATLAS_RELEASE_DIR", str(ROOT / "releases"))).resolve()
TOKEN = os.environ.get("ATLAS_DEPLOYMENT_AGENT_TOKEN", "")
PROJECT = os.environ.get("ATLAS_PROJECT_NAME", "atlas")
REPO_URL = "https://github.com/wwxst/atlas-console.git"
API = "https://api.github.com/repos/wwxst/atlas-console"
STATE_FILE = RELEASE_DIR / "updater-status.json"
CACHE_FILE = RELEASE_DIR / "latest-release-cache.json"
WORKER_IMAGE_PREFIX = "atlas-updater:git-"
MODE = os.environ.get("ATLAS_DEPLOYMENT_MODE", "docker")
TAG_RE = re.compile(r"^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
RELEASE_ID_RE = re.compile(r"^[0-9a-f]{40}_[0-9a-f]{40}$")
LOCK = threading.Lock()


def read_json(path: Path, fallback=None):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError):
        return fallback


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    temp.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    os.chmod(temp, 0o600)
    os.replace(temp, path)


def read_manifest(path: Path):
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
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


def current_release_id():
    return read_manifest(RELEASE_DIR / "current.env")


def previous_release_available():
    return bool(read_manifest(RELEASE_DIR / "current.env") and
                read_manifest(RELEASE_DIR / "previous.env"))


def version_metadata(release_id: str | None):
    if not release_id or not RELEASE_ID_RE.fullmatch(release_id):
        return None
    return read_json(RELEASE_DIR / "versions" / (release_id + ".json"))


def current_version():
    release_id = current_release_id()
    metadata = version_metadata(release_id)
    if metadata and isinstance(metadata.get("version"), str):
        return metadata["version"]
    # Bootstrap installations may predate version metadata. Their initial checkout
    # is immutable installation context until the first managed release.
    package = read_json(ROOT / "atlas-console" / "frontend" / "package.json", {})
    version = package.get("version") if isinstance(package, dict) else None
    if isinstance(version, str) and re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", version):
        if release_id:
            write_json(RELEASE_DIR / "versions" / (release_id + ".json"),
                       {"version": version, "tag": "v" + version, "releaseUrl": None})
        return version
    return None


def is_newer_release(candidate: str | None, installed: str | None) -> bool:
    """Only upgrade stable SemVer; never downgrade a customer installation."""
    if candidate is None or TAG_RE.fullmatch("v" + candidate) is None:
        return False
    if installed is None:
        return True
    if TAG_RE.fullmatch("v" + installed) is None:
        return False
    if "-" in candidate or "-" in installed:
        # Prereleases are not offered by /releases/latest.
        return False
    return tuple(map(int, candidate.split("."))) > tuple(map(int, installed.split(".")))


def fetch_latest_release(force=False):
    cached = read_json(CACHE_FILE, {})
    if not force and isinstance(cached, dict) and time.time() - cached.get("fetchedAt", 0) < 300:
        return cached.get("release"), cached.get("error")
    request = Request(API + "/releases/latest", headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "atlas-console-customer-updater",
        "X-GitHub-Api-Version": "2022-11-28",
    })
    try:
        with urlopen(request, timeout=12) as response:
            payload = json.loads(response.read(2 * 1024 * 1024).decode("utf-8"))
        tag = payload.get("tag_name", "")
        if payload.get("draft") or payload.get("prerelease") or not TAG_RE.fullmatch(tag):
            raise ValueError("The latest GitHub Release has an invalid stable version tag")
        release = {
            "tag": tag,
            "version": tag[1:],
            "name": str(payload.get("name") or tag)[:200],
            "url": str(payload.get("html_url") or ""),
            "publishedAt": payload.get("published_at"),
            "body": str(payload.get("body") or "")[:12000],
        }
        if not release["url"].startswith("https://github.com/wwxst/atlas-console/releases/"):
            raise ValueError("The release URL is outside the official Atlas repository")
        write_json(CACHE_FILE, {"fetchedAt": time.time(), "release": release, "error": None})
        return release, None
    except HTTPError as exc:
        message = "GitHub 暂时无法提供版本信息" if exc.code != 404 else "GitHub 尚未发布正式版本"
        write_json(CACHE_FILE, {"fetchedAt": time.time(), "release": None, "error": message})
        return None, message
    except (URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError):
        if cached.get("release"):
            return cached["release"], "GitHub 版本检查暂时失败，显示上次成功结果"
        return None, "无法连接 GitHub 版本发布服务"


def worker_running(container: str | None):
    if not container or not re.fullmatch(r"atlas-update-worker-[0-9a-f-]{36}", container):
        return False
    try:
        result = subprocess.run(
            ["docker", "inspect", "--format", "{{.State.Running}}", container],
            capture_output=True, text=True, timeout=5, check=False)
        return result.returncode == 0 and result.stdout.strip() == "true"
    except (OSError, subprocess.TimeoutExpired):
        return False


def status_payload():
    saved = read_json(STATE_FILE, {})
    if not isinstance(saved, dict):
        saved = {}
    active = (worker_running(saved.get("workerContainer")) if MODE == "docker"
              else __import__("native_agent").is_running(saved.get("workerPid")))
    within_launch_grace = time.time() - saved.get("startedAt", 0) < 5
    if saved.get("state") == "in_progress" and not active and not within_launch_grace:
        saved.update({"state": "completed", "conclusion": "failure",
                      "message": "更新进程意外退出，请检查服务器更新日志",
                      "finishedAt": time.time()})
        write_json(STATE_FILE, saved)
    latest, check_error = fetch_latest_release()
    current = current_version()
    latest_version = latest.get("version") if latest else None
    update_available = is_newer_release(latest_version, current)
    state = saved.get("state", "idle")
    if not latest and state == "idle":
        state = "no_release" if check_error == "GitHub 尚未发布正式版本" else "release_unavailable"
    return {
        "configured": bool(TOKEN),
        "state": state,
        "conclusion": saved.get("conclusion"),
        "runUrl": latest.get("url") if latest else saved.get("releaseUrl"),
        "title": latest.get("name") if latest else saved.get("title"),
        "requestId": saved.get("requestId"),
        "rollbackAvailable": previous_release_available(),
        "runId": None,
        "recoveryAvailable": False,
        "currentVersion": current,
        "latestVersion": latest_version,
        "latestReleaseUrl": latest.get("url") if latest else None,
        "releaseNotes": latest.get("body") if latest else None,
        "publishedAt": latest.get("publishedAt") if latest else None,
        "updateAvailable": update_available,
        "releaseCheckError": check_error,
        "message": saved.get("message"),
    }


def worker_image() -> str:
    # Run the detached worker from this installation's currently active,
    # version-pinned image, so even the updater itself can be rolled back.
    current = current_release_id()
    if current is None or not RELEASE_ID_RE.fullmatch(current):
        raise RuntimeError("无法确定当前更新执行器版本")
    return WORKER_IMAGE_PREFIX + current.split("_")[1]


def start_worker(operation: str, tag: str | None = None, release_id: str | None = None):
    with LOCK:
        if MODE == "baota":
            from native_agent import start
            return start(operation, tag, release_id)
        saved = read_json(STATE_FILE, {})
        if isinstance(saved, dict) and saved.get("state") == "in_progress" and worker_running(saved.get("workerContainer")):
            raise RuntimeError("已有更新任务正在执行，请等待完成")
        image = worker_image()
        request_id = str(uuid.uuid4())
        container = "atlas-update-worker-" + request_id
        payload = {
            "state": "in_progress", "conclusion": None, "operation": operation,
            "requestId": request_id, "workerContainer": container,
            "startedAt": time.time(), "message": "正在准备更新任务",
        }
        write_json(STATE_FILE, payload)
        command = [
            "docker", "run", "--detach", "--rm", "--name", container,
            "--label", "com.atlas.updater.worker=true",
            "--volume", "/var/run/docker.sock:/var/run/docker.sock",
            "--volume", str(ROOT) + ":" + str(ROOT),
            "--env", "ATLAS_INSTALL_ROOT=" + str(ROOT),
            "--env", "ATLAS_ENV_FILE=" + str(ENV_FILE),
            "--env", "ATLAS_RELEASE_DIR=" + str(RELEASE_DIR),
            "--env", "ATLAS_PROJECT_NAME=" + PROJECT,
            "--env", "ATLAS_REPO_URL=" + REPO_URL,
            "--env", "ATLAS_GITHUB_API=" + API,
            image,
            "python3", "/opt/atlas-updater/worker.py",
            "--operation", operation,
            "--request-id", request_id,
        ]
        if tag:
            command.extend(["--tag", tag])
        if release_id:
            command.extend(["--release-id", release_id])
        try:
            result = subprocess.run(command, capture_output=True, text=True, timeout=30, check=False)
        except (OSError, subprocess.TimeoutExpired) as exc:
            payload.update({"state": "completed", "conclusion": "failure",
                            "message": "无法启动更新执行器", "finishedAt": time.time()})
            write_json(STATE_FILE, payload)
            raise RuntimeError("无法启动服务器更新执行器") from exc
        if result.returncode != 0:
            payload.update({"state": "completed", "conclusion": "failure",
                            "message": "无法启动更新执行器", "finishedAt": time.time()})
            write_json(STATE_FILE, payload)
            raise RuntimeError("无法启动服务器更新执行器")
        return payload


class Handler(BaseHTTPRequestHandler):
    server_version = "AtlasUpdater/1.0"

    def log_message(self, fmt, *args):
        print("atlas-updater: " + fmt % args, flush=True)

    def respond(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + TOKEN
        return bool(TOKEN) and hmac.compare_digest(supplied, expected)

    def do_GET(self):
        if self.path == "/health":
            return self.respond(200, {"ok": True})
        if self.path != "/api/status":
            return self.respond(404, {"message": "Not found"})
        if not self.authorized():
            return self.respond(401, {"message": "Unauthorized"})
        return self.respond(200, status_payload())

    def do_POST(self):
        if not self.authorized():
            return self.respond(401, {"message": "Unauthorized"})
        if self.path not in ("/api/deploy", "/api/rollback", "/api/rollback-history", "/api/recover"):
            return self.respond(404, {"message": "Not found"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length < 0 or length > 4096:
                return self.respond(413, {"message": "Request too large"})
            raw = self.rfile.read(length) if length else b"{}"
            body = json.loads(raw.decode("utf-8"))
            if not isinstance(body, dict):
                return self.respond(400, {"message": "Invalid request"})
            if self.path == "/api/recover":
                return self.respond(200, status_payload())
            if self.path == "/api/deploy":
                latest, error = fetch_latest_release(force=True)
                if not latest:
                    return self.respond(409, {"message": error or "没有可用的正式版本"})
                current = current_version()
                if not is_newer_release(latest["version"], current):
                    return self.respond(409, {"message": "当前已经是最新版本或当前版本高于发布版本"})
                operation = "deploy"
                tag, release_id = latest["tag"], None
            elif self.path == "/api/rollback":
                if not previous_release_available():
                    return self.respond(409, {"message": "没有可回滚的上一版本"})
                operation, tag, release_id = "rollback", None, None
            else:
                release_id = body.get("releaseId")
                if not isinstance(release_id, str) or not RELEASE_ID_RE.fullmatch(release_id):
                    return self.respond(400, {"message": "历史版本编号无效"})
                archive = RELEASE_DIR / "history" / (release_id + ".env")
                if not archive.is_file() or read_manifest(archive) != release_id:
                    return self.respond(404, {"message": "该历史版本不可回滚"})
                operation, tag = "rollback-history", None
            result = start_worker(operation, tag, release_id)
            return self.respond(202, result)
        except RuntimeError as exc:
            return self.respond(409, {"message": str(exc)})
        except (ValueError, UnicodeDecodeError):
            return self.respond(400, {"message": "Invalid request"})


def main():
    if not TOKEN or len(TOKEN) < 48:
        raise SystemExit("ATLAS_DEPLOYMENT_AGENT_TOKEN must contain at least 48 characters")
    RELEASE_DIR.mkdir(parents=True, exist_ok=True)
    if MODE not in ("docker", "baota"):
        raise SystemExit("Invalid deployment mode")
    bind = "127.0.0.1" if MODE == "baota" else "0.0.0.0"
    server = ThreadingHTTPServer((bind, 8090), Handler)
    print("Atlas customer updater listening on " + bind, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
