"""Host-native worker launcher. Docker is never invoked in BaoTa mode."""
from __future__ import annotations

import os
import re
import subprocess
import sys
import time
import uuid
from pathlib import Path

import server

WORKER = Path(__file__).resolve().parent / "native_worker.py"


def is_running(pid) -> bool:
    if not isinstance(pid, int) or pid < 2:
        return False
    try:
        cmdline = (Path("/proc") / str(pid) / "cmdline").read_bytes().replace(b"\x00", b" ")
        return b"native_worker.py" in cmdline
    except (OSError, ValueError):
        return False


def start(operation: str, tag: str | None, release_id: str | None):
    saved = server.read_json(server.STATE_FILE, {})
    if isinstance(saved, dict) and saved.get("state") == "in_progress" and is_running(saved.get("workerPid")):
        raise RuntimeError("已有更新任务正在执行")
    if operation not in ("deploy", "rollback", "rollback-history"):
        raise RuntimeError("不支持的更新操作")
    if tag is not None and not server.TAG_RE.fullmatch(tag):
        raise RuntimeError("无效的正式版本")
    if release_id is not None and not server.RELEASE_ID_RE.fullmatch(release_id):
        raise RuntimeError("无效的历史版本")
    request_id = str(uuid.uuid4())
    payload = {
        "state": "in_progress", "conclusion": None, "operation": operation,
        "requestId": request_id, "startedAt": time.time(),
        "message": "正在启动本机原生更新任务",
    }
    server.write_json(server.STATE_FILE, payload)
    command = [sys.executable, str(WORKER), "--operation", operation,
               "--request-id", request_id]
    if tag:
        command.extend(["--tag", tag])
    if release_id:
        command.extend(["--release-id", release_id])
    log_dir = server.RELEASE_DIR / "update-logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / (request_id + ".log")
    child_env = {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "ATLAS_INSTALL_ROOT": str(server.ROOT),
        "ATLAS_RELEASE_DIR": str(server.RELEASE_DIR),
        "PYTHONUNBUFFERED": "1",
    }
    try:
        with log_path.open("ab") as output:
            child = subprocess.Popen(
                command, env=child_env, cwd=server.ROOT,
                stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT,
                start_new_session=True, close_fds=True)
        # Do not overwrite a completed state if a very fast worker already exited.
        current = server.read_json(server.STATE_FILE, {})
        if current.get("requestId") == request_id and current.get("state") == "in_progress":
            current["workerPid"] = child.pid
            server.write_json(server.STATE_FILE, current)
        return current
    except (OSError, subprocess.SubprocessError) as exc:
        payload.update({"state": "completed", "conclusion": "failure",
                        "message": "本机更新任务启动失败", "finishedAt": time.time()})
        server.write_json(server.STATE_FILE, payload)
        raise RuntimeError("本机更新任务启动失败") from exc
