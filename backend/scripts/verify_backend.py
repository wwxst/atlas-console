#!/usr/bin/env python3
"""Run Atlas backend verification against PostgreSQL and Redis.

The PostgreSQL server must be running and DB_URL / DB_USERNAME / DB_PASSWORD must
point to a disposable test database. Integration tests are enabled only when
ATLAS_POSTGRES_TEST_URL is set.
"""
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    if not os.environ.get("ATLAS_POSTGRES_TEST_URL", "").startswith("jdbc:postgresql://"):
        print("Set ATLAS_POSTGRES_TEST_URL to a disposable PostgreSQL JDBC database.", file=sys.stderr)
        return 2
    if not os.environ.get("REDIS_HOST"):
        print("Set REDIS_HOST and REDIS_PORT to a disposable Redis instance.", file=sys.stderr)
        return 2
    wrapper = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    command = [str(wrapper), "-B", "-ntp", "clean", "verify"]
    return subprocess.run(command, cwd=ROOT, env=os.environ.copy(), check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
