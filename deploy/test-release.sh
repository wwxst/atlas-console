#!/usr/bin/env bash
# Repeatable release-state regression checks without needing a production server.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
mkdir -p "$workdir/bin" "$workdir/images"
touch "$workdir/.env"
export ATLAS_ENV_FILE="$workdir/.env"
export ATLAS_RELEASE_DIR="$workdir/releases"
export TEST_DOCKER_DIR="$workdir"

cat > "$workdir/bin/git" <<'FAKE_GIT'
#!/usr/bin/env bash
if [[ "$2" == */backend ]]; then
  echo "$TEST_BACK_SHA"
else
  echo "$TEST_FRONT_SHA"
fi
FAKE_GIT

cat > "$workdir/bin/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
set -Eeuo pipefail
key() { printf '%s' "${1//:/_}"; }
if [[ "${1:-}" == image && "${2:-}" == inspect ]]; then
  test -f "$TEST_DOCKER_DIR/images/$(key "$3")"
  exit $?
fi
if [[ "${1:-}" != compose ]]; then exit 7; fi
while (($#)); do
  case "$1" in
    build)
      if [[ "${2:-}" == mysql ]]; then
        touch "$TEST_DOCKER_DIR/images/atlas-mysql_flyway"
      else
        touch "$TEST_DOCKER_DIR/images/$(key "atlas-console:$ATLAS_FRONTEND_TAG")"
        touch "$TEST_DOCKER_DIR/images/$(key "atlas-backend:$ATLAS_BACKEND_TAG")"
      fi
      exit 0
      ;;
    up)
      if [[ -f "$TEST_DOCKER_DIR/fail-next-up" ]]; then
        rm -f "$TEST_DOCKER_DIR/fail-next-up"
        echo "Simulated failed health check" >&2
        exit 1
      fi
      printf '%s\n%s\n' "$ATLAS_FRONTEND_TAG" "$ATLAS_BACKEND_TAG" > "$TEST_DOCKER_DIR/running"
      exit 0
      ;;
    port)
      echo "127.0.0.1:8088"
      exit 0
      ;;
  esac
  shift
done
exit 8
FAKE_DOCKER
cat > "$workdir/bin/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
exit 0
FAKE_CURL
chmod +x "$workdir/bin/"*
export PATH="$workdir/bin:$PATH"

# This test runs with sibling checkout atlas-console + backend in GitHub Actions.
test -d "$(dirname "$root")/backend/.git"

export TEST_FRONT_SHA="$(printf 'a%.0s' {1..40})"
export TEST_BACK_SHA="$(printf 'b%.0s' {1..40})"
export VERIFIED_FRONTEND_SHA="$TEST_FRONT_SHA"
export VERIFIED_BACKEND_SHA="$TEST_BACK_SHA"
bash "$root/deploy/release.sh" deploy
grep -q "frontend=git-$TEST_FRONT_SHA" "$ATLAS_RELEASE_DIR/current.env"
test ! -e "$ATLAS_RELEASE_DIR/previous.env"

# Even with valid source checkouts, a mismatched tested pair must never deploy.
if VERIFIED_BACKEND_SHA="$(printf '0%.0s' {1..40})" bash "$root/deploy/release.sh" deploy; then
  echo "Unverified backend commit unexpectedly deployed" >&2
  exit 1
fi
grep -q "frontend=git-$TEST_FRONT_SHA" "$ATLAS_RELEASE_DIR/current.env"

if bash "$root/deploy/release.sh" rollback; then
  echo "Rollback must fail when no previous release exists" >&2
  exit 1
fi

export TEST_FRONT_SHA="$(printf 'c%.0s' {1..40})"
export TEST_BACK_SHA="$(printf 'd%.0s' {1..40})"
export VERIFIED_FRONTEND_SHA="$TEST_FRONT_SHA"
export VERIFIED_BACKEND_SHA="$TEST_BACK_SHA"
bash "$root/deploy/release.sh" deploy
grep -q 'frontend=git-aaaaaaaa' "$ATLAS_RELEASE_DIR/previous.env"
grep -q 'frontend=git-cccccccc' "$ATLAS_RELEASE_DIR/current.env"

# Simulate unhealthy v3. v2 must be reactivated and manifests stay unchanged.
touch "$workdir/fail-next-up"
export TEST_FRONT_SHA="$(printf 'e%.0s' {1..40})"
export TEST_BACK_SHA="$(printf 'f%.0s' {1..40})"
if export VERIFIED_FRONTEND_SHA="$TEST_FRONT_SHA"
export VERIFIED_BACKEND_SHA="$TEST_BACK_SHA"
bash "$root/deploy/release.sh" deploy; then
  echo "Unhealthy deployment must fail" >&2
  exit 1
fi
grep -q 'frontend=git-cccccccc' "$ATLAS_RELEASE_DIR/current.env"
grep -q 'frontend=git-aaaaaaaa' "$ATLAS_RELEASE_DIR/previous.env"
grep -q 'git-cccccccc' "$workdir/running"

bash "$root/deploy/release.sh" rollback
grep -q 'frontend=git-aaaaaaaa' "$ATLAS_RELEASE_DIR/current.env"
grep -q 'frontend=git-cccccccc' "$ATLAS_RELEASE_DIR/previous.env"
grep -q 'git-aaaaaaaa' "$workdir/running"

# The archive keeps all successfully deployed version pairs, not just current/previous.
history_count="$(find "$ATLAS_RELEASE_DIR/history" -type f -name '*.env' | wc -l)"
[[ "$history_count" -eq 2 ]] || { echo "Expected two archived releases" >&2; exit 1; }

# Deploy a third healthy release, then restore v2 (which is not the immediate previous v1).
export TEST_FRONT_SHA="$(printf 'e%.0s' {1..40})"
export TEST_BACK_SHA="$(printf 'f%.0s' {1..40})"
export VERIFIED_FRONTEND_SHA="$TEST_FRONT_SHA"
export VERIFIED_BACKEND_SHA="$TEST_BACK_SHA"
bash "$root/deploy/release.sh" deploy
grep -q 'frontend=git-aaaaaaaa' "$ATLAS_RELEASE_DIR/previous.env"
target="$(printf 'c%.0s' {1..40})_$(printf 'd%.0s' {1..40})"
bash "$root/deploy/release.sh" rollback-history "$target"
grep -q 'frontend=git-cccccccc' "$ATLAS_RELEASE_DIR/current.env"
grep -q 'frontend=git-eeeeeeee' "$ATLAS_RELEASE_DIR/previous.env"
grep -q 'git-cccccccc' "$workdir/running"
[[ "$(find "$ATLAS_RELEASE_DIR/history" -type f -name '*.env' | wc -l)" -eq 3 ]]

# Unknown, malformed and missing-image releases must not change the active version.
if bash "$root/deploy/release.sh" rollback-history '../current.env'; then
  echo "Invalid historical release ID unexpectedly accepted" >&2; exit 1
fi
if bash "$root/deploy/release.sh" rollback-history "$(printf '9%.0s' {1..40})_$(printf '8%.0s' {1..40})"; then
  echo "Unknown historical release unexpectedly accepted" >&2; exit 1
fi
rm -f "$workdir/images/atlas-console_git-$(printf 'a%.0s' {1..40})"
first="$(printf 'a%.0s' {1..40})_$(printf 'b%.0s' {1..40})"
if bash "$root/deploy/release.sh" rollback-history "$first"; then
  echo "Missing archived image unexpectedly activated" >&2; exit 1
fi
grep -q 'frontend=git-cccccccc' "$ATLAS_RELEASE_DIR/current.env"

echo "Release rollback and history regression checks passed"
