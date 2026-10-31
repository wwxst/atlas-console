#!/usr/bin/env bash
# Exercise the real Compose wrapper with an explicit executable test double.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"
cat > "$work/bin/docker" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
for name in COMPOSE_PROJECT_NAME ATLAS_PROJECT_NAME ATLAS_NETWORK_PREFIX ATLAS_HTTP_PORT ATLAS_DB_URL ATLAS_DB_USERNAME ATLAS_DB_PASSWORD DB_PASSWORD POSTGRES_PASSWORD POSTGRES_ADMIN_PASSWORD JWT_SECRET STORAGE_ENCRYPTION_KEY ATLAS_FLYWAY_BASELINE_ON_MIGRATE ATLAS_DEPLOYMENT_ENABLED ATLAS_DEPLOYMENT_GITHUB_TOKEN; do
  if [[ -v "$name" ]]; then echo "Ambient $name leaked into Compose" >&2; exit 1; fi
done
[[ "$ATLAS_FRONTEND_TAG" == git-test && "$ATLAS_BACKEND_TAG" == git-test ]]
[[ "$ATLAS_RELEASE_DIR" == "$TEST_CONTEXT_DIR/releases" ]]
printf '%s\n' "$@" > "$TEST_CONTEXT_DIR/args"
SH
chmod +x "$work/bin/docker"
export PATH="$work/bin:$PATH" TEST_CONTEXT_DIR="$work"
for name in COMPOSE_PROJECT_NAME ATLAS_PROJECT_NAME ATLAS_NETWORK_PREFIX ATLAS_HTTP_PORT ATLAS_DB_URL ATLAS_DB_USERNAME ATLAS_DB_PASSWORD DB_PASSWORD POSTGRES_PASSWORD POSTGRES_ADMIN_PASSWORD JWT_SECRET STORAGE_ENCRYPTION_KEY ATLAS_FLYWAY_BASELINE_ON_MIGRATE ATLAS_DEPLOYMENT_ENABLED ATLAS_DEPLOYMENT_GITHUB_TOKEN; do
  export "$name=unrelated-production-context"
done
export ATLAS_FRONTEND_TAG=git-test ATLAS_BACKEND_TAG=git-test ATLAS_RELEASE_DIR="$work/releases"
env_file="$work/atlas.env"
project_name=atlas-isolated-test
compose_args=(-f "$work/compose.yaml")
eval "$(sed -n '/^compose() {/,/^}/p' "$root/deploy/release.sh")"
compose config
grep -Fxq -- '--project-name' "$work/args"
grep -Fxq -- "$project_name" "$work/args"
grep -Fxq -- "$env_file" "$work/args"
echo 'Compose uses explicit installation identity and private env, ignoring ambient settings (test double)'
