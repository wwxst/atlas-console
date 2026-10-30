#!/usr/bin/env bash
# Behavior regression with a Docker test double; never contacts a Docker daemon.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
asset_script="${ATLAS_FRONTEND_ASSET_SCRIPT:-$root/deploy/test-frontend-assets.sh}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir "$work/bin"
cat > "$work/bin/docker" <<'SH'
#!/usr/bin/env bash
set -Eeuo pipefail
printf '%s\n' "${1:-missing}" >> "$TEST_ASSET_DOCKER_CALLS"
case "${1:-}" in
  build) exit 0 ;;
  create)
    echo 'Simulated container name conflict; existing container is untouched' >&2
    exit 42
    ;;
  *) echo 'Unexpected Docker operation after failed create' >&2; exit 81 ;;
esac
SH
chmod +x "$work/bin/docker"
export PATH="$work/bin:$PATH" TEST_ASSET_DOCKER_CALLS="$work/docker-calls"

if bash "$asset_script" > "$work/output" 2>&1; then
  echo 'Asset test unexpectedly succeeded after a container name conflict' >&2
  exit 1
else
  status=$?
fi
[[ "$status" == 42 ]] || { echo 'Asset test did not preserve the create failure' >&2; exit 1; }
grep -Fq 'Simulated container name conflict' "$work/output" || {
  echo 'Asset test never reached the simulated container conflict' >&2; exit 1;
}
printf 'build\ncreate\n' > "$work/expected-calls"
cmp -s "$work/expected-calls" "$TEST_ASSET_DOCKER_CALLS" || {
  echo 'Asset cleanup called Docker after failed create; an existing container could be deleted' >&2
  exit 1
}
echo 'Frontend asset test preserves existing containers after create conflicts (test double)'
