#!/usr/bin/env python3
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import server
import worker


class ReleaseValidationTests(unittest.TestCase):
    def test_only_semver_release_tags_are_accepted(self):
        for tag in ("v0.1.0", "v1.20.300", "v2.0.0-rc.1"):
            self.assertTrue(server.TAG_RE.fullmatch(tag), tag)
            self.assertTrue(worker.TAG_RE.fullmatch(tag), tag)
        for tag in ("main", "latest", "0.1.0", "v01.2.3", "v1.2", "v1.2.3/evil"):
            self.assertFalse(server.TAG_RE.fullmatch(tag), tag)
            self.assertFalse(worker.TAG_RE.fullmatch(tag), tag)

    def test_release_id_must_be_two_full_commit_shas(self):
        valid = "a" * 40 + "_" + "b" * 40
        self.assertTrue(server.RELEASE_ID_RE.fullmatch(valid))
        self.assertFalse(server.RELEASE_ID_RE.fullmatch("../current.env"))
        self.assertFalse(server.RELEASE_ID_RE.fullmatch("a" * 40 + "_main"))

    def test_manifest_parser_rejects_tampered_or_extra_lines(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "current.env"
            front, back = "a" * 40, "b" * 40
            path.write_text(f"frontend=git-{front}\nbackend=git-{back}\n", encoding="utf-8")
            self.assertEqual(front + "_" + back, server.read_manifest(path))
            path.write_text(f"frontend=git-{front}\nbackend=git-{back}\nextra=1\n", encoding="utf-8")
            self.assertIsNone(server.read_manifest(path))
            path.write_text("frontend=git-../current\nbackend=git-" + back + "\n", encoding="utf-8")
            self.assertIsNone(server.read_manifest(path))


class VersionOrderingTests(unittest.TestCase):
    def test_newer_stable_versions_are_recognized_without_downgrades(self):
        self.assertTrue(server.is_newer_release("0.1.1", "0.1.0"))
        self.assertTrue(server.is_newer_release("1.0.0", "0.99.99"))
        self.assertFalse(server.is_newer_release("0.1.1", "0.1.2"))
        self.assertFalse(server.is_newer_release("0.1.1", "0.1.1"))
        self.assertFalse(server.is_newer_release("0.1.0-rc.1", "0.1.0"))
        self.assertFalse(server.is_newer_release(None, "0.1.0"))


class ReleaseDiscoveryTests(unittest.TestCase):
    def test_public_release_is_cached_and_normalized(self):
        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *_):
                return False
            def read(self, _limit):
                return json.dumps({
                    "tag_name": "v0.1.1", "name": "Atlas 0.1.1", "draft": False,
                    "prerelease": False, "html_url": "https://github.com/wwxst/atlas-console/releases/tag/v0.1.1",
                    "published_at": "2026-11-02T00:00:00Z", "body": "Bug fixes",
                }).encode()

        with tempfile.TemporaryDirectory() as directory:
            with patch.object(server, "CACHE_FILE", Path(directory) / "latest.json"), \
                 patch.object(server, "urlopen", return_value=Response()) as fetch:
                release, error = server.fetch_latest_release(force=True)
                self.assertIsNone(error)
                self.assertEqual("0.1.1", release["version"])
                self.assertEqual("Bug fixes", release["body"])
                again, error = server.fetch_latest_release()
                self.assertEqual(release, again)
                self.assertIsNone(error)
                fetch.assert_called_once()

    def test_non_release_urls_and_prereleases_are_rejected(self):
        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *_):
                return False
            def read(self, _limit):
                return json.dumps({
                    "tag_name": "v0.1.2", "draft": False, "prerelease": True,
                    "html_url": "https://github.com/wwxst/atlas-console/releases/tag/v0.1.2",
                }).encode()

        with tempfile.TemporaryDirectory() as directory:
            with patch.object(server, "CACHE_FILE", Path(directory) / "latest.json"), \
                 patch.object(server, "urlopen", return_value=Response()):
                release, error = server.fetch_latest_release(force=True)
                self.assertIsNone(release)
                self.assertEqual("无法连接 GitHub 版本发布服务", error)


class OfflineRollbackTests(unittest.TestCase):
    def test_rollback_uses_local_script_without_contacting_github(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / "atlas-console" / "deploy" / "release.sh"
            script.parent.mkdir(parents=True)
            script.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            release_dir = root / "releases"
            release_dir.mkdir()
            request_id = "12345678-1234-1234-1234-123456789abc"
            (release_dir / "updater-status.json").write_text(
                json.dumps({"requestId": request_id, "state": "in_progress"}),
                encoding="utf-8")
            result = type("Result", (), {"returncode": 0})()
            with patch.object(worker, "ROOT", root), \
                 patch.object(worker, "RELEASE_DIR", release_dir), \
                 patch.object(worker, "STATE_FILE", release_dir / "updater-status.json"), \
                 patch.object(worker, "request_json", side_effect=AssertionError("GitHub must remain unused")), \
                 patch.object(worker, "manifest_id", return_value=None), \
                 patch.object(worker.subprocess, "run", return_value=result) as runner, \
                 patch.object(sys, "argv", ["worker.py", "--operation", "rollback",
                                            "--request-id", request_id]):
                worker.run()
                self.assertEqual("completed", worker.read_json(worker.STATE_FILE)["state"])
                self.assertEqual("success", worker.read_json(worker.STATE_FILE)["conclusion"])
                command = runner.call_args.args[0]
                self.assertEqual(["bash", str(script), "rollback"], command)


class StatusTests(unittest.TestCase):
    def test_update_available_compares_release_versions_not_commit_dates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release_dir = root / "releases"
            release_dir.mkdir()
            front = back = "a" * 40
            (release_dir / "current.env").write_text(
                f"frontend=git-{front}\nbackend=git-{back}\n", encoding="utf-8")
            (release_dir / "versions").mkdir()
            (release_dir / "versions" / (front + "_" + back + ".json")).write_text(
                json.dumps({"version": "0.1.0"}), encoding="utf-8")
            with patch.object(server, "RELEASE_DIR", release_dir), \
                 patch.object(server, "STATE_FILE", release_dir / "status.json"), \
                 patch.object(server, "TOKEN", "x" * 64), \
                 patch.object(server, "fetch_latest_release", return_value=({
                     "version": "0.1.1", "url": "https://github.com/wwxst/atlas-console/releases/tag/v0.1.1",
                     "name": "Atlas 0.1.1", "body": "Release notes", "publishedAt": None,
                 }, None)):
                status = server.status_payload()
                self.assertTrue(status["configured"])
                self.assertEqual("0.1.0", status["currentVersion"])
                self.assertEqual("0.1.1", status["latestVersion"])
                self.assertTrue(status["updateAvailable"])
                self.assertFalse(status["rollbackAvailable"])

    def test_worker_start_command_is_fixed_and_never_receives_a_shell_string(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            release_dir = root / "releases"
            release_dir.mkdir()
            front = "a" * 40
            (release_dir / "current.env").write_text(
                f"frontend=git-{front}\nbackend=git-{front}\n", encoding="utf-8")
            with patch.object(server, "ROOT", root), \
                 patch.object(server, "ENV_FILE", root / ".env"), \
                 patch.object(server, "RELEASE_DIR", release_dir), \
                 patch.object(server, "STATE_FILE", release_dir / "status.json"), \
                 patch.object(server, "PROJECT", "atlas-customer"), \
                 patch.object(server.subprocess, "run", return_value=type("Result", (), {
                     "returncode": 0, "stdout": "worker-id", "stderr": ""
                 })()) as run:
                payload = server.start_worker("deploy", "v0.1.1")
                self.assertEqual("in_progress", payload["state"])
                command = run.call_args.args[0]
                self.assertIn("atlas-updater:git-" + "a" * 40, command)
                self.assertIn("/opt/atlas-updater/worker.py", command)
                self.assertIn("v0.1.1", command)
                self.assertNotIn("sh", command)
                self.assertFalse(any("github_pat" in item.lower() for item in command))
                self.assertEqual(payload["requestId"], server.read_json(server.STATE_FILE)["requestId"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
