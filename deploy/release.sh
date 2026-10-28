#!/usr/bin/env bash
# Transactional application-image deployment and one-step rollback.
# This does NOT reverse database migrations or restore persistent data.
set -Eeuo pipefail
umask 077

operation="${1:-}"
if [[ "$operation" != deploy && "$operation" != rollback ]]; then
  echo "Usage: release.sh deploy|rollback" >&2
  exit 2
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workspace="$(dirname "$project_root")"
compose_file="$project_root/deploy/compose.yaml"
env_file="${ATLAS_ENV_FILE:-/opt/atlas/.env}"
state_dir="${ATLAS_RELEASE_DIR:-/opt/atlas/releases}"
current="$state_dir/current.env"
previous="$state_dir/previous.env"
mkdir -p "$state_dir"
chmod 700 "$state_dir"
test -r "$env_file" || { echo "Production .env not available" >&2; exit 1; }
test -d "$workspace/backend/.git" || { echo "Backend checkout missing" >&2; exit 1; }

# Fail before doing anything if another deploy process is already executing.
exec 9>"$state_dir/deploy.lock"
flock -n 9 || { echo "Another release operation is running" >&2; exit 1; }

compose() {
  docker compose --env-file "$env_file" -f "$compose_file" "$@"
}

validate_tag() {
  [[ "$1" =~ ^git-[a-f0-9]{40}$ ]] || {
    echo "Release manifest has invalid tag" >&2
    return 1
  }
}

load_manifest() {
  local source="$1"
  test -r "$source" || return 1
  local line_count
  line_count="$(wc -l < "$source")"
  [[ "$line_count" -eq 2 ]] || return 1
  local front back
  front="$(sed -n '1s/^frontend=//p' "$source")"
  back="$(sed -n '2s/^backend=//p' "$source")"
  validate_tag "$front"
  validate_tag "$back"
  ATLAS_FRONTEND_TAG="$front"
  ATLAS_BACKEND_TAG="$back"
  export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
}

save_manifest() {
  local destination="$1"
  local front="$2"
  local back="$3"
  validate_tag "$front"
  validate_tag "$back"
  local temp
  temp="$(mktemp "$state_dir/.manifest.XXXXXXXX")"
  printf 'frontend=%s\nbackend=%s\n' "$front" "$back" > "$temp"
  chmod 600 "$temp"
  mv -f "$temp" "$destination"
}

assert_images() {
  docker image inspect "atlas-console:$ATLAS_FRONTEND_TAG" >/dev/null
  docker image inspect "atlas-backend:$ATLAS_BACKEND_TAG" >/dev/null
}

check_site() {
  # Compose waits for application health; also verify the actual HTTP listener.
  local port
  port="$(compose port frontend 8080 | awk -F: 'END {print $NF}')"
  [[ "$port" =~ ^[0-9]+$ ]] || return 1
  curl --fail --silent --show-error --retry 3 --retry-delay 1 \
    "http://127.0.0.1:$port/" >/dev/null
}

activate() {
  assert_images || return 1
  compose up --detach --no-build --wait --wait-timeout 240 || return 1
  check_site || return 1
}

if [[ "$operation" == rollback ]]; then
  test -f "$current" || { echo "No deployed release to roll back" >&2; exit 1; }
  test -f "$previous" || { echo "No previous release to roll back to" >&2; exit 1; }
  load_manifest "$current"
  old_front="$ATLAS_FRONTEND_TAG"
  old_back="$ATLAS_BACKEND_TAG"
  load_manifest "$previous"
  next_front="$ATLAS_FRONTEND_TAG"
  next_back="$ATLAS_BACKEND_TAG"
  echo "Reverting application images to previous release"
  if ! activate; then
    echo "Rollback health check failed; restoring current release" >&2
    ATLAS_FRONTEND_TAG="$old_front"
    ATLAS_BACKEND_TAG="$old_back"
    export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
    activate || echo "ALERT: current release recovery failed" >&2
    exit 1
  fi
  save_manifest "$current" "$next_front" "$next_back"
  save_manifest "$previous" "$old_front" "$old_back"
  echo "Rollback completed and previous/current versions swapped"
  exit 0
fi

front_sha="$(git -C "$project_root" rev-parse HEAD)"
back_sha="$(git -C "$workspace/backend" rev-parse HEAD)"
ATLAS_FRONTEND_TAG="git-$front_sha"
ATLAS_BACKEND_TAG="git-$back_sha"
export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
validate_tag "$ATLAS_FRONTEND_TAG"
validate_tag "$ATLAS_BACKEND_TAG"

if [[ -f "$current" ]]; then
  load_manifest "$current"
  stable_front="$ATLAS_FRONTEND_TAG"
  stable_back="$ATLAS_BACKEND_TAG"
  ATLAS_FRONTEND_TAG="git-$front_sha"
  ATLAS_BACKEND_TAG="git-$back_sha"
  export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
else
  stable_front=""
  stable_back=""
fi

# MySQL initialization image is only built during the first installation.
if ! docker image inspect atlas-mysql:local >/dev/null 2>&1; then
  compose build mysql
fi
compose build frontend backend

if ! activate; then
  echo "Deployment failed health verification" >&2
  if [[ -n "$stable_front" ]]; then
    ATLAS_FRONTEND_TAG="$stable_front"
    ATLAS_BACKEND_TAG="$stable_back"
    export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
    if activate; then
      echo "Previous running application version restored automatically" >&2
    else
      echo "ALERT: automatic rollback also failed; operator intervention required" >&2
    fi
  else
    echo "No previous version exists; first install needs manual recovery" >&2
  fi
  exit 1
fi

if [[ -n "$stable_front" && ("$stable_front" != "git-$front_sha" || "$stable_back" != "git-$back_sha") ]]; then
  save_manifest "$previous" "$stable_front" "$stable_back"
fi
save_manifest "$current" "git-$front_sha" "git-$back_sha"
echo "Production application image release recorded successfully"
