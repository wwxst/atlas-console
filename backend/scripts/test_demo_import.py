"""Windows demo importer regression tests using disposable PostgreSQL databases.

Run with PSQL_BIN pointing to psql.exe; DB_* credentials follow the importer.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]


class DemoImportTest(unittest.TestCase):
    def setUp(self):
        self.client = Path(os.environ["PSQL_BIN"]).resolve()
        self.database = "atlas_demo_test_" + uuid.uuid4().hex[:12]
        self.env = os.environ.copy()
        self.env.update(DB_NAME=self.database, DB_HOST=self.env.get("DB_HOST", "127.0.0.1"),
                        DB_PORT=self.env.get("DB_PORT", "5432"),
                        DB_USERNAME=self.env.get("DB_USERNAME", "postgres"))
        self.env["PGPASSWORD"] = self.env.get("DB_PASSWORD", "123456")
        self.env["PGOPTIONS"] = "-c TimeZone=UTC"
        self.workspace = tempfile.TemporaryDirectory(prefix="atlas demo test ")
        self.addCleanup(self.workspace.cleanup)
        self.directory = Path(self.workspace.name)
        for source in (ROOT / "demosql").iterdir():
            if source.is_file():
                shutil.copy2(source, self.directory)
        self.sql("CREATE DATABASE " + self.database, database="postgres")
        self.addCleanup(self.sql, "DROP DATABASE " + self.database + " WITH (FORCE)", database="postgres")
        schema = (ROOT / "src/main/resources/db/migration/V1__initial_schema.sql").read_text(encoding="utf-8")
        self.sql(schema)

    def sql(self, statement, database=None):
        command = [str(self.client), "-X", "-v", "ON_ERROR_STOP=1",
                   "--host=" + self.env["DB_HOST"], "--port=" + self.env["DB_PORT"],
                   "--username=" + self.env["DB_USERNAME"], "--dbname=" + (database or self.database),
                   "--tuples-only", "--no-align"]
        result = subprocess.run(command, input=statement.encode("utf-8"), env=self.env,
                                capture_output=True, timeout=30)
        self.assertEqual(result.returncode, 0, "PostgreSQL operation failed")
        return result.stdout.decode("utf-8").strip()

    def run_import(self):
        return subprocess.run(["cmd.exe", "/d", "/c", str(self.directory / "import_demo_data.bat")],
                              input=b"\n" * 8, env=self.env, capture_output=True, timeout=60)

    def assert_imported(self, result):
        self.assertEqual(result.returncode, 0, "Demo importer failed")
        self.assertIn("[DONE] 演示数据导入完成。", result.stdout.decode("utf-8"))
        self.assertEqual(self.sql("SELECT COUNT(*) FROM sys_user WHERE username LIKE 'u%'; "
                                  "SELECT COUNT(*) FROM user_account;").splitlines(), ["50", "100"])
        self.assertEqual(self.sql("SELECT nickname FROM sys_user WHERE username='u10020';"), "杨静")

    def test_client_on_path_imports_both_tables(self):
        self.env.pop("PSQL_BIN", None)
        self.env["PATH"] = str(self.client.parent) + os.pathsep + self.env["PATH"]
        self.assert_imported(self.run_import())

    def test_explicit_client_with_spaces_imports_both_tables(self):
        self.env["PSQL_BIN"] = str(self.client)
        self.assert_imported(self.run_import())

    def test_connection_failure_reports_postgres_error(self):
        self.env["PSQL_BIN"] = str(self.client)
        self.env["DB_NAME"] = self.database + "_missing"
        result = self.run_import()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"does not exist", result.stderr)
        self.assertNotIn(b"[DONE]", result.stdout)

    def test_verification_failure_does_not_report_success(self):
        self.env["PSQL_BIN"] = str(self.client)
        (self.directory / "03_verify.sql").write_text("SELECT missing_demo_column;", encoding="utf-8")
        result = self.run_import()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(b"does not exist", result.stderr)
        self.assertNotIn(b"[DONE]", result.stdout)


if __name__ == "__main__":
    unittest.main()
