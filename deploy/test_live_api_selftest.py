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
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"code":50000,"msg":"SECRET-response","data":null}')


class LiveApiSelfTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Stub)
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
                self.mod.verify_stack("atlas-acceptance-test")

    def test_failed_http_check_has_its_own_scenario_name(self):
        report = self.mod.Report("test-run", "atlas-acceptance-test")
        gate = self.mod.Gate(self.mod.Client(self.base), report, "atlas-acceptance-test", "test-run", {})
        with self.assertRaises(self.mod.Failure):
            gate.check("specific broken endpoint", "GET", "/business-failure")
        self.assertEqual(report.data["scenarios"], [{"name": "specific broken endpoint", "status": "fail",
                         "reason": "expected HTTP/business 200/200, got 200/50000"}])

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
