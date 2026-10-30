"""Local HTTP stub checks for the acceptance client; these are not live acceptance."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import threading
import unittest
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("test-live-api.py")
sys.dont_write_bytecode = True


class Stub(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.headers.get("Authorization") in self.server.revoked:
            self.send_response(401)
            self.end_headers()
            self.wfile.write(b'{"code":40102,"msg":null,"data":null}')
            return
        self.send_response(302 if self.path == "/redirect" else 200)
        if self.path == "/redirect":
            self.send_header("Location", "/ok")
        self.end_headers()
        if self.path == "/html":
            self.wfile.write(b"<html>not the API</html>")
        else:
            code = 50000 if self.path == "/business-failure" else 200
            self.wfile.write(json.dumps({"code": code, "msg": "SECRET-response", "data": {"ok": True}}).encode())

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.path in ("/api/sys-user/auth/logout", "/api/user/auth/logout"):
            token = self.headers.get("Authorization")
            revoked = token in self.server.revoked
            self.server.revoked.add(token)
            self.send_response(401 if revoked else 200)
            self.end_headers()
            self.wfile.write(b'{"code":40102,"data":null}' if revoked else b'{"code":200,"data":null}')
            return
        if self.path in ("/api/sys-user/auth/refresh", "/api/user/auth/refresh"):
            self.send_response(401)
            self.end_headers()
            self.wfile.write(b'{"code":40104,"data":null}')
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"code":50000,"msg":"SECRET-response","data":null}')


class LiveApiSelfTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Stub)
        cls.server.revoked = set()
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        self.assertTrue(SCRIPT.is_file(), "live acceptance implementation is missing")
        spec = importlib.util.spec_from_file_location("live_api", SCRIPT)
        self.mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.mod)
        self.base = "http://127.0.0.1:" + str(self.server.server_port)

    def test_http_200_with_business_failure_is_failure(self):
        client = self.mod.Client(self.base)
        with self.assertRaises(self.mod.Failure) as caught:
            client.check("GET", "/business-failure", token="SECRET-token")
        self.assertNotIn("SECRET", str(caught.exception))
        self.assertEqual(client.check("GET", "/ok"), {"ok": True})

    def test_html_and_redirect_do_not_pass_as_api(self):
        client = self.mod.Client(self.base)
        for path in ("/html", "/redirect"):
            with self.subTest(path=path), self.assertRaises(self.mod.Failure):
                client.check("GET", path)

    def test_report_failure_does_not_serialize_exception_or_credentials(self):
        report = self.mod.Report("test-run", "atlas-acceptance-test")
        def bad():
            raise ValueError("SECRET-password SECRET-token SECRET-code")
        self.assertFalse(report.scenario("expected failure", bad))
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "report.json"
            report.write(target)
            text = target.read_text(encoding="utf-8")
            self.assertNotIn("SECRET", text)
            result = json.loads(text)
            self.assertEqual(result["status"], "fail")
            self.assertEqual(result["scenarios"][0]["status"], "fail")

    def test_redis_access_only_reads_run_owned_verification_key(self):
        reader = self.mod.CodeReader("atlas-acceptance-test", "run123")
        with patch.object(self.mod.subprocess, "run") as command:
            command.return_value.returncode = 0
            command.return_value.stdout = "123456\n"
            self.assertEqual(reader.read("run123@example.com", "REGISTER", "EMAIL"), "123456")
            args = command.call_args.args[0]
            self.assertEqual(args, ["docker", "exec", "atlas-acceptance-test-redis-1", "redis-cli", "--raw", "GET",
                                    "auth:code:register:email:run123@example.com"])
            with self.assertRaises(self.mod.Failure):
                reader.read("someone-else@example.com", "REGISTER", "EMAIL")
            self.assertEqual(command.call_count, 1)

    def test_stack_guard_rejects_production_project_and_foreign_labels(self):
        with self.assertRaises(self.mod.Failure):
            self.mod.validate_project("atlas")
        with patch.object(self.mod.subprocess, "run") as command:
            command.return_value.returncode = 0
            command.return_value.stdout = json.dumps({"com.docker.compose.project": "another", "com.docker.compose.service": "redis"})
            with self.assertRaises(self.mod.Failure):
                self.mod.verify_stack("atlas-acceptance-test", self.base)

    def stack_inspection(self, host_ip="127.0.0.1", port=None, image=True, runtime_ip=None, frontend_project=None):
        port = str(port or self.server.server_port)
        def output(arguments):
            service = arguments[-1].removeprefix("atlas-acceptance-test-").removesuffix("-1")
            labels = {"com.docker.compose.project": "atlas-acceptance-test", "com.docker.compose.service": service}
            if service != "frontend" or arguments[2] == "{{json .Config.Labels}}":
                return json.dumps(labels)
            if frontend_project:
                labels["com.docker.compose.project"] = frontend_project
            return json.dumps({"labels": labels, "image": "sha256:" + "a" * 64 if image else "",
                               "configuredImage": "atlas-console:test",
                               "hostBindings": {"8080/tcp": [{"HostIp": host_ip, "HostPort": port}]},
                               "ports": {"8080/tcp": [{"HostIp": runtime_ip or host_ip, "HostPort": port}]}})
        return output

    def test_client_rejects_nonliteral_loopback_http_origins(self):
        for url in ("http://example.com:8088", "http://localhost:8088", "https://127.0.0.1:8088", "http://[::1]:8088"):
            with self.subTest(url=url), self.assertRaises(self.mod.Failure):
                self.mod.Client(url)

    def test_stack_origin_is_bound_to_its_frontend_loopback_port(self):
        with patch.object(self.mod, "docker_output", side_effect=self.stack_inspection()):
            self.mod.verify_stack("atlas-acceptance-test", self.base)

    def test_guard_rejects_unrelated_origins_before_any_http(self):
        cases = [
            ("remote origin", "http://example.com:8088", self.stack_inspection()),
            ("DNS alias", "http://localhost:8088", self.stack_inspection()),
            ("HTTPS alias", "https://127.0.0.1:8088", self.stack_inspection()),
            ("IPv6 alias", "http://[::1]:8088", self.stack_inspection()),
            ("wrong project port", self.base, self.stack_inspection(port=self.server.server_port + 1)),
            ("public port binding", self.base, self.stack_inspection(host_ip="0.0.0.0")),
            ("public runtime binding only", self.base, self.stack_inspection(runtime_ip="0.0.0.0")),
            ("public configured binding only", self.base, self.stack_inspection(host_ip="0.0.0.0", runtime_ip="127.0.0.1")),
            ("foreign frontend project", self.base, self.stack_inspection(frontend_project="other-acceptance")),
            ("missing image identity", self.base, self.stack_inspection(image=False)),
        ]
        for name, base_url, inspection in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                report = Path(directory) / "report.json"
                args = [str(SCRIPT), "--base-url", base_url, "--project", "atlas-acceptance-test",
                        "--report", str(report), "--credentials-file", str(Path(directory) / "unused.json")]
                with patch.object(self.mod.sys, "argv", args), \
                     patch.object(self.mod, "docker_output", side_effect=inspection), \
                     patch.object(self.mod, "load_credentials", return_value={"username": "stub", "password": "SECRET"}), \
                     patch.object(self.mod.Client, "raw") as http, contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(self.mod.main(), 1)
                    http.assert_not_called()
                data = json.loads(report.read_text(encoding="utf-8"))
                self.assertEqual(data["scenarios"][0]["status"], "fail")

    def test_failed_http_check_has_its_own_scenario_name(self):
        report = self.mod.Report("test-run", "atlas-acceptance-test")
        gate = self.mod.Gate(self.mod.Client(self.base), report, "atlas-acceptance-test", "test-run", {})
        with self.assertRaises(self.mod.Failure):
            gate.check("specific broken endpoint", "GET", "/business-failure")
        self.assertEqual(report.data["scenarios"], [{"name": "specific broken endpoint", "status": "fail",
                         "reason": "expected HTTP/business 200/200, got 200/50000"}])

    def test_logout_checks_http_authentication_before_repeated_controller_call(self):
        report = self.mod.Report("test-run", "atlas-acceptance-test")
        gate = self.mod.Gate(self.mod.Client(self.base), report, "atlas-acceptance-test", "test-run", {})
        self.assertTrue(callable(getattr(gate, "logout_pair", None)), "shared HTTP logout contract verifier is missing")
        for kind in ("sys-user", "user"):
            pair = {"accessToken": "stub-access-" + kind, "refreshToken": "stub-refresh-" + kind}
            gate.logout_pair(kind, pair)
        self.assertTrue(all(item["status"] == "pass" for item in report.data["scenarios"]))

    def test_cli_real_http_business_failure_exits_nonzero_without_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            credentials = Path(directory) / "private.json"
            credentials.write_text('{"username":"stub-admin","password":"SECRET-password"}', encoding="utf-8")
            credentials.chmod(0o600)
            report = Path(directory) / "report.json"
            args = [str(SCRIPT), "--base-url", self.base, "--project", "atlas-acceptance-test",
                    "--report", str(report), "--credentials-file", str(credentials)]
            stdout = io.StringIO()
            with patch.object(self.mod.sys, "argv", args), patch.object(self.mod, "verify_stack"), contextlib.redirect_stdout(stdout):
                result = self.mod.main()
            self.assertEqual(result, 1)
            self.assertNotIn("SECRET", stdout.getvalue() + report.read_text(encoding="utf-8"))
            data = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(data["status"], "fail")
            self.assertTrue(any(item["name"] == "system authentication contracts" and item["status"] == "fail"
                                for item in data["scenarios"]))


if __name__ == "__main__":
    unittest.main()
