#!/usr/bin/env bash
set -Eeuo pipefail
root="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
bash -n "$root/install.sh"
help="$(bash "$root/install.sh" --help)"
grep -q 'curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh' <<<"$help"
grep -q -- '--db-mode docker|external' <<<"$help"
grep -q 'This script installs the application stack only' <<<"$help"
grep -q 'chmod 600 "$ENV_FILE"' "$root/install.sh"
grep -q 'ATLAS_FLYWAY_BASELINE_ON_MIGRATE false' "$root/install.sh"
grep -q 'VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit"' "$root/install.sh"
grep -q 'host.docker.internal:host-gateway' "$root/deploy/compose.yaml"
test -f "$root/deploy/compose.mysql.yaml"
echo "Installer syntax, help, secret-file permissions, and database-mode checks passed"
