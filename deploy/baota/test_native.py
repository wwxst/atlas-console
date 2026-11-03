"""BaoTa native updater security, activation and offline rollback regression tests."""
import hashlib
import io
import json
import os
import sys
import tarfile
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import MagicMock, patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "updater"))
sys.path.insert(0, str(HERE))

import server
import native_agent
import native_worker as native


class NativeCase(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.releases = self.root / "releases"
        self.apps = self.releases / "apps"
        self.runtime = self.root / "runtime"
        self.apps.mkdir(parents=True)
        self.runtime.mkdir()
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        for obj, name, value in [
            (native, "ROOT", self.root),
            (native, "RELEASE_DIR", self.releases),
            (native, "APPS", self.apps),
            (native, "CURRENT", self.runtime / "current"),
            (server, "ROOT", self.root),
            (server, "RELEASE_DIR", self.releases),
            (server, "STATE_FILE", self.releases / "updater-status.json"),
            (server, "CACHE_FILE", self.releases / "latest-release-cache.json"),
        ]:
            self.stack.enter_context(patch.object(obj, name, value))

    def app(self, symbol):
        sha = symbol * 40
        rid = sha + "_" + sha
        directory = self.apps / rid
        (directory / "frontend").mkdir(parents=True)
        (directory / "frontend" / "index.html").write_text("<html>Atlas</html>")
        (directory / "backend.jar").write_bytes(b"jar-test")
        return rid

    def frontend_archive(self, path, name="index.html", type_=tarfile.REGTYPE):
        with tarfile.open(path, "w:gz") as bundle:
            data = b"<html>Atlas</html>"
            info = tarfile.TarInfo(name)
            info.type = type_
            if type_ == tarfile.SYMTYPE:
                info.linkname = "/etc/passwd"
                data = b""
            info.size = len(data)
            bundle.addfile(info, io.BytesIO(data))


class ReleaseManifestTest(NativeCase):
    def test_native_release_id_is_two_matching_git_revisions(self):
        rid = "a" * 40 + "_" + "a" * 40
        self.assertEqual(rid, native.installed_id(self.releases / "missing.env") or rid)
        self.assertEqual("frontend=git-" + "a" * 40 + "\nbackend=git-" + "a" * 40 + "\n",
                         native.manifest(rid))
        for value in ("../etc/passwd", "a" * 40 + "_" + "b" * 40, "main"):
            with self.assertRaises(ValueError):
                native.manifest(value)

    def test_sha256_manifest_rejects_tampering_and_unknown_names(self):
        for name in native.ASSETS:
            (self.root / name).write_bytes(name.encode())
        sums = "".join(
            hashlib.sha256(name.encode()).hexdigest() + "  " + name + "\n"
            for name in native.ASSETS)
        (self.root / "SHA256SUMS").write_text(sums)
        native.verify_checksums(self.root)
        (self.root / "atlas-backend.jar").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "SHA256"):
            native.verify_checksums(self.root)
        (self.root / "SHA256SUMS").write_text(sums + "0" * 64 + "  ../evil\n")
        with self.assertRaisesRegex(ValueError, "校验清单"):
            native.verify_checksums(self.root)

    def test_tar_extraction_does_not_allow_path_traversal_or_symlinks(self):
        for index, (name, type_) in enumerate([
            ("../../outside.txt", tarfile.REGTYPE),
            ("/etc/passwd", tarfile.REGTYPE),
            ("assets/link", tarfile.SYMTYPE),
        ]):
            path = self.root / f"bad-{index}.tar.gz"
            self.frontend_archive(path, name, type_)
            with self.assertRaises(ValueError):
                native.safe_extract_frontend(path, self.root / f"target-{index}")
        self.assertFalse((self.root / "outside.txt").exists())

    def test_good_frontend_archive_is_extractable(self):
        path = self.root / "frontend.tar.gz"
        self.frontend_archive(path)
        destination = self.root / "site"
        native.safe_extract_frontend(path, destination)
        self.assertTrue((destination / "index.html").is_file())


class NativeActivationTest(NativeCase):
    def test_activate_records_release_and_offline_rollback_restores_older_app(self):
        old_id, new_id = self.app("a"), self.app("b")
        with patch.object(native, "restart_and_check"):
            native.activate(old_id, "0.1.0", "v0.1.0")
            self.assertEqual(old_id, native.installed_id(self.releases / "current.env"))
            self.assertEqual(self.apps / old_id, native.CURRENT.resolve())
            native.activate(new_id, "0.1.1", "v0.1.1")
            self.assertEqual(new_id, native.installed_id(self.releases / "current.env"))
            self.assertEqual(old_id, native.installed_id(self.releases / "previous.env"))
            with patch.object(native.worker, "request_json", side_effect=AssertionError("no network")):
                native.rollback(old_id)
            self.assertEqual(self.apps / old_id, native.CURRENT.resolve())
            self.assertEqual(old_id, native.installed_id(self.releases / "current.env"))
            self.assertEqual(new_id, native.installed_id(self.releases / "previous.env"))
        self.assertEqual("0.1.1", json.loads(
            (self.releases / "versions" / (new_id + ".json")).read_text())["version"])

    def test_failed_health_check_restores_symlink_without_changing_current_manifest(self):
        old_id, new_id = self.app("a"), self.app("b")
        with patch.object(native, "restart_and_check"):
            native.activate(old_id, "0.1.0", "v0.1.0")
        with patch.object(native, "restart_and_check",
                          side_effect=[RuntimeError("new process unhealthy"), None]) as health:
            with self.assertRaisesRegex(RuntimeError, "恢复到旧版"):
                native.activate(new_id, "0.1.1", "v0.1.1")
            self.assertEqual(2, health.call_count)
        self.assertEqual(self.apps / old_id, native.CURRENT.resolve())
        self.assertEqual(old_id, native.installed_id(self.releases / "current.env"))

    def test_rollbacks_must_be_in_local_history(self):
        candidate = "c" * 40 + "_" + "c" * 40
        with self.assertRaises(ValueError):
            native.rollback(candidate)


class NativeDownloadTest(NativeCase):
    def test_deploy_checks_official_assets_and_does_not_build_from_untrusted_source(self):
        tag = "v0.1.1"
        archive = self.root / "original.tar.gz"
        self.frontend_archive(archive)
        jar = b"spring-boot-fat-jar"
        sums = (hashlib.sha256(archive.read_bytes()).hexdigest() + "  atlas-frontend.tar.gz\n"
                + hashlib.sha256(jar).hexdigest() + "  atlas-backend.jar\n")
        names = (*native.ASSETS, "SHA256SUMS")
        expected = {name: f"https://github.com/wwxst/atlas-console/releases/download/{tag}/{name}"
                    for name in names}
        def fake_download(_tag, name, dest):
            dest.write_bytes({"atlas-frontend.tar.gz": archive.read_bytes(),
                              "atlas-backend.jar": jar,
                              "SHA256SUMS": sums.encode()}[name])
        release = {"tag_name": tag, "assets": [
            {"name": name, "browser_download_url": url} for name, url in expected.items()
        ], "html_url": f"https://github.com/wwxst/atlas-console/releases/tag/{tag}"}
        with patch.object(native.worker, "release_for_tag", return_value=release), \
             patch.object(native, "get_commit", return_value="a" * 40), \
             patch.object(native, "download", side_effect=fake_download), \
             patch.object(native, "activate") as activate:
            native.deploy(tag)
            self.assertEqual("a" * 40 + "_" + "a" * 40, activate.call_args.args[0])
            self.assertTrue((self.apps / ("a" * 40 + "_" + "a" * 40) / "frontend" / "index.html").exists())
            self.assertTrue((self.apps / ("a" * 40 + "_" + "a" * 40) / "backend.jar").exists())

    def test_deploy_refuses_missing_checksum_asset(self):
        tag = "v0.1.1"
        release = {"tag_name": tag, "assets": []}
        with patch.object(native.worker, "release_for_tag", return_value=release):
            with self.assertRaisesRegex(RuntimeError, "缺少可信"):
                native.deploy(tag)


class NativeAgentTest(NativeCase):
    def test_spawn_never_uses_docker_or_shell_and_does_not_pass_token(self):
        pid = 38071
        with patch.object(native_agent, "WORKER", self.root / "native_worker.py"), \
             patch.object(native_agent.subprocess, "Popen", return_value=MagicMock(pid=pid)) as spawn:
            self.stack.enter_context(patch.object(server, "TOKEN", "secret" * 11))
            result = native_agent.start("deploy", "v0.1.1", None)
        command = spawn.call_args.args[0]
        self.assertIn("--operation", command)
        self.assertNotIn("docker", command)
        self.assertNotIn("shell", command)
        self.assertTrue(spawn.call_args.kwargs["start_new_session"])
        self.assertNotIn("ATLAS_DEPLOYMENT_AGENT_TOKEN", spawn.call_args.kwargs["env"])
        self.assertEqual(pid, result["workerPid"])

    def test_server_binds_only_to_loopback_in_baota_mode(self):
        listener = MagicMock()
        with patch.object(server, "MODE", "baota"), \
             patch.object(server, "TOKEN", "x" * 64), \
             patch.object(server, "ThreadingHTTPServer", return_value=listener) as factory:
            server.main()
        self.assertEqual(("127.0.0.1", 8090), factory.call_args.args[0])
        listener.serve_forever.assert_called_once()


if __name__ == "__main__":
    unittest.main(verbosity=2)
