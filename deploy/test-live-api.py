#!/usr/bin/env python3
"""HTTP acceptance against an ALREADY RUNNING, isolated test Compose stack.

Never starts/stops services, connects to SQL, clears Redis, or deletes volumes.
Only newly created system accounts are deleted. Ordinary users have no delete
API and remain as run-labelled test records. A system sentinel and its local
avatar also remain for a separate restart/persistence check. Credentials must
be a private JSON file containing username/password; values never enter reports.
The origin must be http://127.0.0.1:<port>, matching this project's frontend
8080/tcp publication in Docker. HTTPS and reverse proxies are checked separately.

Example (run on the test Docker host):
  python3 deploy/test-live-api.py --base-url http://127.0.0.1:18088 \
    --project atlas-acceptance-docker --report /root/acceptance.json \
    --credentials-file /root/atlas-acceptance-credentials.json
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener
import uuid


class Failure(Exception):
    """A safe, script-authored explanation; never wrap server or process text."""


def require(condition, message):
    if not condition:
        raise Failure(message)


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_origin(base_url):
    match = re.fullmatch(r"http://127\.0\.0\.1:([1-9][0-9]{0,4})/?", base_url)
    require(match is not None and int(match[1]) <= 65535,
            "base URL must be literal http://127.0.0.1:<port>")
    return match[1]


class Client:
    def __init__(self, base_url):
        validate_origin(base_url)
        self.base = base_url.rstrip("/")
        self.http = build_opener(ProxyHandler({}), NoRedirect())

    def raw(self, method, path, body=None, token=None, content_type="application/json"):
        require(path.startswith("/") and not path.startswith("//"), "API path must be local")
        headers = {"Content-Type": content_type}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = Request(self.base + path, data=body, headers=headers, method=method)
        try:
            response = self.http.open(request, timeout=20)
        except HTTPError as error:
            response = error
        except (URLError, OSError, TimeoutError):
            raise Failure("HTTP connection failed; transport details withheld") from None
        with response:
            return response.status, response.read(), response.headers

    def check(self, method, path, payload=None, token=None, status=200, code=200,
              raw_body=None, content_type="application/json"):
        data = raw_body if raw_body is not None else (
            None if payload is None else json.dumps(payload).encode("utf-8"))
        actual_status, raw, _ = self.raw(method, path, data, token, content_type)
        try:
            body = json.loads(raw)
        except (ValueError, UnicodeError):
            raise Failure("response is not JSON") from None
        require(isinstance(body, dict), "response must be a JSON object")
        actual_code = body.get("code")
        # Only numeric status values can enter diagnostics; response msg/data cannot.
        safe_code = actual_code if type(actual_code) is int else "non-numeric"
        require((actual_status, actual_code) == (status, code),
                f"expected HTTP/business {status}/{code}, got {actual_status}/{safe_code}")
        require("data" in body, "API response is missing data")
        return body["data"]


class Report:
    def __init__(self, run_id, project):
        self.data = {"run_id": run_id, "project": project,
                     "scope": "real HTTP API on explicitly isolated existing test stack",
                     "started_at": datetime.now(timezone.utc).isoformat(),
                     "scenarios": [], "sentinels": []}

    def add(self, name, status, reason=None):
        item = {"name": name, "status": status}
        if reason:
            item["reason"] = reason
        self.data["scenarios"].append(item)

    def scenario(self, name, operation):
        start = time.monotonic()
        try:
            operation()
        except Exception as error:
            # Arbitrary exception messages may contain tokens, contacts or passwords.
            reason = str(error) if isinstance(error, Failure) else type(error).__name__
            self.add(name, "fail", reason)
            self.data["scenarios"][-1]["seconds"] = round(time.monotonic() - start, 3)
            return False
        self.add(name, "pass")
        self.data["scenarios"][-1]["seconds"] = round(time.monotonic() - start, 3)
        return True

    def write(self, path):
        counts = {status: sum(s["status"] == status for s in self.data["scenarios"])
                  for status in ("pass", "fail", "untested")}
        self.data.update(status="fail" if counts["fail"] else "pass", counts=counts,
                         finished_at=datetime.now(timezone.utc).isoformat())
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.data, indent=2) + "\n", encoding="utf-8")


def validate_project(project):
    require(bool(re.fullmatch(r"[a-z0-9][a-z0-9_-]{0,62}", project))
            and ("acceptance" in project or "test" in project),
            "Compose project must be explicitly named acceptance/test")


def docker_output(arguments):
    try:
        result = subprocess.run(["docker", *arguments], capture_output=True, text=True,
                                timeout=20, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Failure("Docker inspection/read unavailable; details withheld") from None
    require(result.returncode == 0, "Docker inspection/read failed; details withheld")
    return result.stdout.strip()


def verify_stack(project, base_url):
    validate_project(project)
    port = validate_origin(base_url)
    # Select public metadata only; never inspect environment variables/secrets.
    for service in ("redis", "backend", "frontend"):
        template = "{{json .Config.Labels}}"
        if service == "frontend":
            template = ('{"labels":{{json .Config.Labels}},"image":{{json .Image}},'
                        '"configuredImage":{{json .Config.Image}},'
                        '"hostBindings":{{json .HostConfig.PortBindings}},'
                        '"ports":{{json .NetworkSettings.Ports}}}')
        output = docker_output(["inspect", "--format", template,
                                f"{project}-{service}-1"])
        metadata = json.loads(output)
        labels = metadata["labels"] if service == "frontend" else metadata
        require(labels.get("com.docker.compose.project") == project
                and labels.get("com.docker.compose.service") == service,
                "container does not belong to the specified test project")
        if service == "frontend":
            require(bool(re.fullmatch(r"sha256:[0-9a-f]{64}", metadata.get("image", "")))
                    and metadata.get("configuredImage", "").startswith("atlas-console:"),
                    "frontend image identity is missing or unexpected")
            expected = [{"HostIp": "127.0.0.1", "HostPort": port}]
            require(all(isinstance(metadata.get(key), dict)
                        and metadata[key].get("8080/tcp") == expected
                        for key in ("hostBindings", "ports")),
                    "base URL must match the specified frontend's exclusive loopback port binding")


class CodeReader:
    def __init__(self, project, run_id):
        self.project, self.run_id = project, run_id
        self.owned_targets = set()

    def read(self, target, scene, target_type):
        require(self.run_id in target or target in self.owned_targets,
                "refusing to read a verification code for an unrelated target")
        require(scene in ("REGISTER", "LOGIN", "RESET_PASSWORD", "REBIND_OLD", "REBIND_NEW")
                and target_type in ("EMAIL", "PHONE"), "invalid verification code key")
        key = f"auth:code:{scene.lower()}:{target_type.lower()}:{target}"
        code = docker_output(["exec", f"{self.project}-redis-1", "redis-cli", "--raw", "GET", key])
        require(bool(re.fullmatch(r"\d{6}", code)), "test verification code was not stored")
        return code


class Gate:
    def __init__(self, client, report, project, run_id, credentials):
        self.client, self.report, self.run_id = client, report, run_id
        self.credentials = credentials
        self.reader = CodeReader(project, run_id)
        self.admin = None
        self.channels = None
        self.owned_sys = {}
        self.sessions = []
        self.storage_type = None

    def check(self, name, method, path, payload=None, token=None, **expected):
        try:
            result = self.client.check(method, path, payload, token, **expected)
        except Exception as error:
            reason = str(error) if isinstance(error, Failure) else type(error).__name__
            self.report.add(name, "fail", reason)
            raise
        self.report.add(name, "pass")
        return result

    def ensure(self, name, condition):
        require(condition, name)
        self.report.add(name, "pass")

    def pair(self, value, account_type):
        require(isinstance(value, dict) and all(isinstance(value.get(key), str) and value[key]
                for key in ("accessToken", "refreshToken")), "login did not return a token pair")
        self.sessions.append((account_type, value))
        return value

    @property
    def token(self):
        return self.admin["accessToken"]

    def login_system(self, username, password):
        return self.pair(self.check("system password login", "POST", "/api/sys-user/auth/login",
                                   {"username": username, "password": password}), "sys-user")

    def login_user(self, target, password, target_type="EMAIL"):
        return self.pair(self.check("ordinary password login", "POST", "/api/user/auth/login",
                                   {"target": target, "targetType": target_type, "password": password}), "user")

    def issue_code(self, target, scene, token=None, target_type="EMAIL"):
        self.check("issue test verification code: " + scene, "POST", "/api/user/auth/send-code",
                   {"target": target, "targetType": target_type, "scene": scene}, token)
        return self.reader.read(target, scene, target_type)

    def login(self):
        self.admin = self.login_system(self.credentials["username"], self.credentials["password"])
        profile = self.check("current system profile", "GET", "/api/sys-user/auth/me", token=self.token)
        self.ensure("acceptance operator is super administrator", profile.get("id") == 1)
        self.check("anonymous management denied", "GET", "/api/sys-user/sys-users", status=401, code=40102)
        self.check("refresh token cannot access resources", "GET", "/api/sys-user/auth/me",
                   token=self.admin["refreshToken"], status=401, code=40102)
        self.check("access token cannot refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": self.token}, status=401, code=40104)
        self.check("system refresh", "POST", "/api/sys-user/auth/refresh",
                   {"refreshToken": self.admin["refreshToken"]})

    def menus(self):
        for resource in ("sys-users", "users"):
            for order in ("asc", "desc"):
                page = self.check(f"menu {resource} list sorted {order}", "GET",
                                  f"/api/sys-user/{resource}?page=1&pageSize=10&createdAtOrder={order}", token=self.token)
                self.ensure(resource + " paging contract", isinstance(page.get("records"), list)
                            and isinstance(page.get("total"), int) and page.get("page") == 1)
                self.ensure(resource + " list omits passwords", all(not any(
                    key in row for key in ("password", "passwordVersion", "authVersion")) for row in page["records"]))
                if resource == "users":
                    self.ensure("ordinary IDs stay JSON strings", all(isinstance(row.get("id"), str)
                                for row in page["records"]))
            self.check(resource + " invalid paging rejected", "GET", f"/api/sys-user/{resource}?page=",
                       token=self.token, status=400, code=40000)
        self.channels = self.check("channel settings menu", "GET", "/api/sys-user/auth-channels", token=self.token)
        self.ensure("channel configuration boolean contract", all(type(self.channels.get(key)) is bool
                    for key in ("phoneEnabled", "phoneCodeEnabled", "emailEnabled", "emailCodeEnabled")))
        settings = self.check("storage settings menu", "GET", "/api/sys-user/storage/settings", token=self.token)
        self.storage_type = settings["storageType"]
        credentials = self.check("OSS credentials status", "GET", "/api/sys-user/storage/oss/credentials", token=self.token)
        self.ensure("OSS credentials query reveals status only", set(credentials) == {"configured"}
                    and type(credentials["configured"]) is bool)
        self.check("storage spaces menu", "GET", "/api/sys-user/storage/spaces?page=1&pageSize=15", token=self.token)
        self.check("storage paging bound", "GET", "/api/sys-user/storage/spaces?pageSize=101",
                   token=self.token, status=400, code=40000)
        deployment = self.check("deployment status menu", "GET", "/api/sys-user/deployment", token=self.token)
        self.check("deployment release history", "GET", "/api/sys-user/deployment/history", token=self.token)
        self.check("login protection metrics", "GET", "/api/sys-user/auth/login-metrics", token=self.token)
        self.report.add("live OSS integration", "untested", "external OSS resources not supplied; configured="
                        + str(credentials["configured"]).lower())
        self.report.add("GitHub Actions runner deployment/rollback", "untested",
                        "external workflow not triggered; configured=" + str(deployment.get("configured") is True).lower())

    def create_system(self, username, nickname, password):
        account = self.check("create run-owned system user", "POST", "/api/sys-user/sys-users",
                             {"username": username, "nickname": nickname, "password": password, "status": 1}, self.token)
        require(type(account.get("id")) is int and account["id"] != 1,
                "created system user has invalid ID")
        self.owned_sys[account["id"]] = username
        self.ensure("created system user matches request", account.get("username") == username)
        return account

    def system_crud(self):
        username = "live" + uuid.uuid4().hex[:14]
        password, new_password = "At-" + secrets.token_hex(8), "At-" + secrets.token_hex(8)
        account = self.create_system(username, "live-" + self.run_id, password)
        path = "/api/sys-user/sys-users/" + str(account["id"])
        listing = self.check("search created system user", "GET", "/api/sys-user/sys-users?keyword=" + username,
                             token=self.token)
        self.ensure("created system user persisted", any(row["id"] == account["id"] for row in listing["records"]))
        edited = self.check("edit created system user", "PUT", path,
                            {"username": username, "nickname": "edited-" + self.run_id}, self.token)
        self.ensure("system nickname saved", edited.get("nickname") == "edited-" + self.run_id)
        device = self.login_system(username, password)
        self.check("ordinary system user cannot read deployment", "GET", "/api/sys-user/deployment",
                   token=device["accessToken"], status=403, code=40301)
        self.check("ordinary system user cannot read releases", "GET", "/api/sys-user/deployment/history",
                   token=device["accessToken"], status=403, code=40301)
        self.check("reset owned system password", "PATCH", path + "/password", {"password": new_password}, self.token)
        self.reject_pair("sys-user", device, "system password reset")
        self.check("old system password rejected", "POST", "/api/sys-user/auth/login",
                   {"username": username, "password": password}, status=401, code=40101)
        device = self.login_system(username, new_password)
        self.check("disable owned system user", "PATCH", path + "/status", {"status": 0}, self.token)
        self.reject_pair("sys-user", device, "system disable")
        self.check("disabled system login rejected", "POST", "/api/sys-user/auth/login",
                   {"username": username, "password": new_password}, status=403, code=40302)
        self.check("enable owned system user", "PATCH", path + "/status", {"status": 1}, self.token)
        self.reject_pair("sys-user", device, "system reenable keeps old sessions invalid")
        device = self.login_system(username, new_password)
        self.logout_pair("sys-user", device)
        device = self.login_system(username, new_password)
        self.check("delete owned system user", "DELETE", path, token=self.token)
        del self.owned_sys[account["id"]]
        self.reject_pair("sys-user", device, "system delete")
        listing = self.check("deleted system user absent", "GET", "/api/sys-user/sys-users?keyword=" + username,
                             token=self.token)
        self.ensure("system delete persisted", all(row["id"] != account["id"] for row in listing["records"]))

    def reject_pair(self, kind, pair, reason):
        path = "/api/user/me" if kind == "user" else "/api/sys-user/auth/me"
        self.check(reason + " rejects access", "GET", path, token=pair["accessToken"], status=401, code=40102)
        self.check(reason + " rejects refresh", "POST", "/api/" + kind + "/auth/refresh",
                   {"refreshToken": pair["refreshToken"]}, status=401, code=40104)

    def logout_pair(self, kind, pair):
        path = "/api/" + kind + "/auth/logout"
        self.check(kind + " logout", "POST", path, token=pair["accessToken"])
        # Session deletion is idempotent, but the second HTTP call cannot pass
        # AccessSessionValidator and therefore never reaches the controller.
        self.check(kind + " revoked token cannot logout again", "POST", path,
                   token=pair["accessToken"], status=401, code=40102)
        self.reject_pair(kind, pair, kind + " logout")

    def set_channel(self, channel, enabled, code_enabled, name):
        self.check(name, "PUT", "/api/sys-user/auth-channels/" + channel,
                   {"enabled": enabled, "codeEnabled": code_enabled}, self.token)

    def channels_and_users(self):
        require(self.channels is not None, "channel snapshot unavailable")
        original = dict(self.channels)
        try:
            self.set_channel("phone", True, True, "enable test phone channel")
            self.set_channel("email", False, True, "disable test email channel")
            target = self.run_id + "@example.com"
            self.check("disabled email channel rejects codes", "POST", "/api/user/auth/send-code",
                       {"target": target, "targetType": "EMAIL", "scene": "REGISTER"}, status=400, code=40010)
            self.check("cannot disable both channels", "PUT", "/api/sys-user/auth-channels/phone",
                       {"enabled": False, "codeEnabled": True}, self.token, status=400, code=40018)
            self.set_channel("email", True, False, "disable test email codes")
            self.check("disabled email code capability", "POST", "/api/user/auth/send-code",
                       {"target": target, "targetType": "EMAIL", "scene": "REGISTER"}, status=400, code=40011)
            self.set_channel("email", True, True, "enable test email codes")
            self.ordinary_user()
        finally:
            # Restore enabled channels first so the both-disabled invariant is preserved.
            for enabled in (True, False):
                for channel in ("phone", "email"):
                    if original[channel + "Enabled"] == enabled:
                        self.report.scenario("restore " + channel + " channel snapshot", lambda c=channel:
                            self.set_channel(c, original[c + "Enabled"], original[c + "CodeEnabled"],
                                             "restore saved " + c + " channel values"))
            self.report.scenario("channel configuration restored exactly", lambda: self.ensure(
                "channel values equal original snapshot",
                self.client.check("GET", "/api/sys-user/auth-channels", token=self.token) == original))

    def ordinary_user(self):
        email = self.run_id + "@example.com"
        password = "At-" + secrets.token_hex(8)
        code = self.issue_code(email, "REGISTER")
        pair = self.pair(self.check("ordinary email registration", "POST", "/api/user/auth/register",
                                   {"target": email, "targetType": "EMAIL", "code": code, "password": password}), "user")
        profile = self.check("ordinary profile", "GET", "/api/user/me", token=pair["accessToken"])
        self.ensure("ordinary generated ID is precise string", isinstance(profile.get("id"), str)
                    and profile["id"].isdigit() and int(profile["id"]) > 9007199254740991)
        user_id = profile["id"]
        self.report.data["sentinels"].append({"kind": "ordinary-user", "id": user_id,
                                              "run_id": self.run_id, "retained": True})
        self.check("ordinary refresh", "POST", "/api/user/auth/refresh", {"refreshToken": pair["refreshToken"]})
        self.check("ordinary role cannot read management", "GET", "/api/sys-user/users",
                   token=pair["accessToken"], status=403, code=40301)
        self.check("admin role cannot read ordinary profile", "GET", "/api/user/me",
                   token=self.token, status=403, code=40301)
        self.check("ordinary profile update", "PUT", "/api/user/me",
                   {"nickname": "live-" + self.run_id, "avatar": None}, pair["accessToken"])
        updated = self.check("ordinary updated profile read", "GET", "/api/user/me", token=pair["accessToken"])
        self.ensure("ordinary profile update persisted", updated["nickname"] == "live-" + self.run_id)
        code = self.issue_code(email, "LOGIN")
        self.pair(self.check("ordinary code login", "POST", "/api/user/auth/login",
                             {"target": email, "targetType": "EMAIL", "code": code}), "user")
        self.check("consumed code cannot login again", "POST", "/api/user/auth/login",
                   {"target": email, "targetType": "EMAIL", "code": code}, status=400, code=40014)
        pair = self.login_user(email, password)
        path = "/api/sys-user/users/" + user_id
        self.check("ordinary role cannot edit managed user", "PUT", path,
                   {"nickname": "forbidden", "phone": None, "email": email}, pair["accessToken"], status=403, code=40301)
        self.check("disable run-owned ordinary user", "PATCH", path + "/status", {"status": 0}, self.token)
        self.reject_pair("user", pair, "ordinary disable rejects issued tokens")
        self.check("disabled ordinary login denied", "POST", "/api/user/auth/login",
                   {"target": email, "targetType": "EMAIL", "password": password}, status=403, code=40303)
        for _ in range(2):
            edited = self.check("admin edits disabled run-owned user idempotently", "PUT", path,
                                {"nickname": " edited-" + self.run_id + " ", "phone": None,
                                 "email": " " + email.upper() + " "}, self.token)
            self.ensure("admin edit normalization and ID", edited["id"] == user_id and edited["email"] == email
                        and edited["nickname"] == "edited-" + self.run_id and edited["status"] == 0)
        self.check("admin edit requires a contact", "PUT", path, {"nickname": "invalid", "phone": None, "email": None},
                   self.token, status=400, code=40000)
        self.check("reenable run-owned ordinary user", "PATCH", path + "/status", {"status": 1}, self.token)
        pair = self.login_user(email, password)
        new_password = "At-" + secrets.token_hex(8)
        code = self.issue_code(email, "RESET_PASSWORD")
        self.check("ordinary authenticated password change", "PUT", "/api/user/me/password",
                   {"target": email, "targetType": "EMAIL", "code": code, "newPassword": new_password}, pair["accessToken"])
        self.check("password change invalidates old refresh", "POST", "/api/user/auth/refresh",
                   {"refreshToken": pair["refreshToken"]}, status=401, code=40104)
        self.check("changed ordinary password rejects old password", "POST", "/api/user/auth/login",
                   {"target": email, "targetType": "EMAIL", "password": password}, status=401, code=40101)
        pair = self.login_user(email, new_password)
        # Only this generated test phone may be read from Redis; no other phone keys.
        phone = "139" + str(secrets.randbelow(100000000)).zfill(8)
        self.reader.owned_targets.add(phone)
        old_code = self.issue_code(email, "REBIND_OLD", pair["accessToken"])
        new_code = self.issue_code(phone, "REBIND_NEW", pair["accessToken"], "PHONE")
        self.check("ordinary phone rebind", "PUT", "/api/user/me/phone",
                   {"oldTarget": email, "oldTargetType": "EMAIL", "oldCode": old_code,
                    "newPhone": phone, "newCode": new_code}, pair["accessToken"])
        new_email = "rebind-" + self.run_id + "@example.com"
        old_code = self.issue_code(phone, "REBIND_OLD", pair["accessToken"], "PHONE")
        new_code = self.issue_code(new_email, "REBIND_NEW", pair["accessToken"])
        self.check("ordinary email rebind", "PUT", "/api/user/me/email",
                   {"oldTarget": phone, "oldTargetType": "PHONE", "oldCode": old_code,
                    "newEmail": new_email, "newCode": new_code}, pair["accessToken"])
        # New email avoids the existing target's 60s RESET_PASSWORD send lock.
        code = self.issue_code(new_email, "RESET_PASSWORD")
        self.check("ordinary forgotten-password reset", "POST", "/api/user/auth/reset-password",
                   {"target": new_email, "targetType": "EMAIL", "code": code, "newPassword": password})
        self.check("forgotten password reset invalidates refresh", "POST", "/api/user/auth/refresh",
                   {"refreshToken": pair["refreshToken"]}, status=401, code=40104)
        pair = self.login_user(new_email, password)
        self.check("final sentinel nickname", "PUT", "/api/user/me",
                   {"nickname": "live-" + self.run_id, "avatar": None}, pair["accessToken"])
        listing = self.check("search retained ordinary sentinel", "GET", "/api/sys-user/users?keyword=" + quote(self.run_id),
                             token=self.token)
        self.ensure("retained ordinary sentinel persisted", any(row["id"] == user_id
                    and row["nickname"] == "live-" + self.run_id for row in listing["records"]))
        self.logout_pair("user", pair)

    def sentinel(self):
        account = self.create_system("sent" + uuid.uuid4().hex[:14], "live-" + self.run_id,
                                     "At-" + secrets.token_hex(8))
        del self.owned_sys[account["id"]]  # Intentionally retained for restart verification.
        sentinel = {"kind": "system-user", "id": account["id"], "run_id": self.run_id,
                    "username": account["username"], "nickname": "live-" + self.run_id, "retained": True}
        self.report.data["sentinels"].append(sentinel)
        if self.storage_type != "local":
            self.report.add("local avatar persistence sentinel", "untested", "active storage type is not local")
            return
        # Real, decodable 1x1 RGBA PNG, generated using standard-library PNG chunks.
        import struct
        import zlib
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
        png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0))
               + chunk(b"IDAT", zlib.compress(b"\x00\x30\x80\xc0\xff")) + chunk(b"IEND", b""))
        boundary = "atlas-" + uuid.uuid4().hex
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"sentinel.png\"\r\n"
                "Content-Type: image/png\r\n\r\n").encode() + png + f"\r\n--{boundary}--\r\n".encode()
        updated = self.check("upload retained local avatar", "POST", f"/api/sys-user/sys-users/{account['id']}/avatar",
                             token=self.token, raw_body=body, content_type="multipart/form-data; boundary=" + boundary)
        avatar = updated.get("avatar")
        require(isinstance(avatar, str) and re.fullmatch(r"/files/\d+", avatar), "avatar URL is not a stable file ID")
        status, downloaded, headers = self.client.raw("GET", avatar)
        self.ensure("anonymous local avatar read is byte-identical", status == 200 and downloaded == png
                    and headers.get("X-Content-Type-Options") == "nosniff")
        status, head, headers = self.client.raw("HEAD", avatar)
        self.ensure("anonymous local avatar HEAD", status == 200 and head == b""
                    and headers.get("Content-Length") == str(len(png)))
        sentinel.update(avatar=avatar, avatar_sha256=hashlib.sha256(png).hexdigest())

    def cleanup(self):
        if self.admin:
            for account_id, username in list(self.owned_sys.items()):
                # No broad deletion: IDs entered this map only after this run's POST.
                self.report.scenario("cleanup own temporary system user", lambda i=account_id:
                    self.check("delete own temporary system account", "DELETE", "/api/sys-user/sys-users/" + str(i), token=self.token))
            # Logout only test-created sessions; invalid/disabled sessions may return 401.
            for kind, pair in self.sessions:
                if pair is self.admin:
                    continue
                try:
                    self.client.check("POST", "/api/" + kind + "/auth/logout", token=pair["accessToken"])
                except Failure:
                    # Already invalidated tokens are expected; prove they cannot refresh.
                    self.report.scenario("cleanup invalid session cannot refresh", lambda k=kind, p=pair:
                        self.client.check("POST", "/api/" + k + "/auth/refresh",
                                          {"refreshToken": p["refreshToken"]}, status=401, code=40104))
            self.report.scenario("operator logout", lambda: self.check("operator session closed", "POST",
                                 "/api/sys-user/auth/logout", token=self.token))
            self.report.scenario("operator logout invalidates both tokens", lambda:
                                 self.reject_pair("sys-user", self.admin, "operator logout"))


def load_credentials(path):
    path = Path(path)
    require(path.is_file(), "credentials file is missing")
    if os.name != "nt":
        require(path.stat().st_mode & 0o077 == 0, "credentials file must have mode 0600 or stricter")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        raise Failure("credentials file could not be read; contents withheld") from None
    require(isinstance(value, dict) and all(isinstance(value.get(key), str) and value[key]
            for key in ("username", "password")), "credentials file requires username and password")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--credentials-file", required=True, type=Path)
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "_" + uuid.uuid4().hex[:6]
    report = Report(run_id, args.project)
    gate = None
    def prepare():
        nonlocal gate
        require(args.report.resolve() != args.credentials_file.resolve(), "report cannot overwrite the credentials file")
        verify_stack(args.project, args.base_url)
        gate = Gate(Client(args.base_url), report, args.project, run_id, load_credentials(args.credentials_file))
    if report.scenario("isolated existing stack and private credentials", prepare):
        try:
            if report.scenario("system authentication contracts", gate.login):
                for name, operation in (("all implemented menu read APIs", gate.menus),
                                        ("system user lifecycle", gate.system_crud),
                                        ("ordinary user lifecycle and reversible channels", gate.channels_and_users),
                                        ("retained persistence sentinels", gate.sentinel)):
                    report.scenario(name, operation)
                    report.write(args.report)
            else:
                report.add("business API scenarios", "untested", "operator authentication prerequisite failed")
        finally:
            report.scenario("close only this run's sessions and temporary accounts", gate.cleanup)
    else:
        report.add("business API scenarios", "untested", "isolated stack/credentials prerequisite failed")
    report.add("real SMS/email delivery", "untested", "development sender and test Redis codes verify API flow only")
    report.add("dashboard and profile placeholder UI", "untested", "no dedicated implemented business API; browser check is separate")
    report.add("restart persistence", "untested", "sentinels retained for an independent restart and read check")
    # Never overwrite a credentials file even on preflight failure.
    if args.report.resolve() == args.credentials_file.resolve():
        print(json.dumps({"status": "fail", "reason": "report cannot overwrite the credentials file"}))
        return 1
    report.write(args.report)
    print(json.dumps({"status": report.data["status"], "run_id": run_id,
                      "counts": report.data["counts"], "sentinels": report.data["sentinels"]}))
    return 1 if report.data["counts"]["fail"] else 0


if __name__ == "__main__":
    sys.exit(main())
