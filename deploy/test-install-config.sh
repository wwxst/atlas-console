#!/usr/bin/env bash
# Real Compose regression plus temporary env probes with no networks or volumes.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
# Load only the serializer, so this test is also safe against an older installer.
eval "$(sed -n '/^write_env() {/,/^}/p' "$root/install.sh")"
declare -F write_env >/dev/null
passwords=("single'quote" 'space # hash $dollar ${NOT_AN_ENV_VAR} "double" \ backslash' 'ends-in-\' '\\' "slash\\'quote" 'literal\n\t $$ $' 'slash\"double' $'  \tsecret with edge whitespace\t  ')
# Read the actual production interpolation expression; do not test a different
# expression in the probe. config output may escape dollars for reloading, so
# assert the final process environment instead of its rendered JSON spelling.
password_expression="$(sed -n 's/^      DB_PASSWORD: //p' "$root/deploy/compose.yaml")"
cat > "$work/probe.yaml" <<'YAML'
services:
  probe:
    image: alpine:3.22
    network_mode: none
    entrypoint: ["sh", "-c", "printf '%s' \"$$DB_PASSWORD\""]
    environment:
YAML
printf '      DB_PASSWORD: %s\n' "$password_expression" >> "$work/probe.yaml"
for password in "${passwords[@]}"; do
  ENV_FILE="$work/atlas.env"
  : > "$ENV_FILE"
  write_env ATLAS_DB_MODE external
  write_env ATLAS_DB_URL 'jdbc:mysql://host.docker.internal:3306/atlas_test'
  write_env ATLAS_DB_USERNAME atlas_test
  write_env ATLAS_DB_PASSWORD "$password"
  write_env DB_PASSWORD unused_test_password
  write_env JWT_SECRET test_only_jwt_secret
  write_env STORAGE_ENCRYPTION_KEY test_only_storage_key
  write_env ATLAS_PROJECT_NAME atlas-config-test
  write_env ATLAS_NETWORK_PREFIX 172.30.91
  write_env ATLAS_RELEASE_DIR "$work/releases"
  printf '%s' "$password" > "$work/expected"
  docker compose --env-file "$ENV_FILE" -f "$root/deploy/compose.yaml" config --format json > "$work/config.json"
  python3 - "$work" <<'PY'
import json, pathlib, sys
work = pathlib.Path(sys.argv[1])
config = json.loads((work / 'config.json').read_text())
assert config['name'] == 'atlas-config-test', 'Compose project is not isolated'
assert config['networks']['default']['ipam']['config'][0]['subnet'] == '172.30.91.0/24'
assert config['services']['frontend']['networks']['default']['ipv4_address'] == '172.30.91.10'
assert config['services']['backend']['environment']['TRUSTED_PROXY_ADDRESSES'] == '172.30.91.10'
assert any(v['source'] == str(work / 'releases') for v in config['services']['backend']['volumes'])
PY
  docker compose --env-file "$ENV_FILE" -p "atlas-env-probe-$$" -f "$work/probe.yaml" \
    run --rm --no-deps -T probe > "$work/actual"
  cmp -s "$work/expected" "$work/actual" || { echo 'Database password changed in the container environment' >&2; exit 1; }
done
echo 'Real Compose password roundtrip and installation isolation checks passed'
