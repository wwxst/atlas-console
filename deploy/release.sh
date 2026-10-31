#!/usr/bin/env bash
# Transactional application-image deployment, one-step and historical rollback.
# This does NOT reverse database migrations or restore persistent data.
set -Eeuo pipefail
umask 077

operation="${1:-}"
target_release="${2:-}"
if [[ "$operation" != deploy && "$operation" != rollback && "$operation" != rollback-history ]]; then
  echo "Usage: release.sh deploy|rollback|rollback-history [release-id]" >&2
  exit 2
fi
if [[ "$operation" == rollback-history && ! "$target_release" =~ ^[a-f0-9]{40}_[a-f0-9]{40}$ ]]; then
  echo "Invalid historical release ID" >&2
  exit 2
fi

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# runuser inherits the root caller's cwd, which may be unreadable after dropping
# privileges. Compose resolves its schema/context from cwd even with absolute -f.
cd "$project_root"
compose_file="$project_root/deploy/compose.yaml"
postgres_compose_file="$project_root/deploy/compose.postgres.yaml"
env_file="${ATLAS_ENV_FILE:-/opt/atlas/.env}"
state_dir="${ATLAS_RELEASE_DIR:-/opt/atlas/releases}"
# The same directory must be mounted into the backend, including isolated installs.
export ATLAS_RELEASE_DIR="$state_dir"
current="$state_dir/current.env"
previous="$state_dir/previous.env"
history_dir="$state_dir/history"
mkdir -p "$history_dir"
chmod 700 "$state_dir" "$history_dir"
test -r "$env_file" || { echo "Production .env not available" >&2; exit 1; }

# Read only these validated non-secret settings; never source secrets as code.
# Ambient values must not select another installation or database mode.
db_mode="$(sed -n 's/^ATLAS_DB_MODE=//p' "$env_file" | tail -n 1 | tr -d "'\"")"
db_mode="${db_mode:-docker}"
project_name="$(sed -n 's/^ATLAS_PROJECT_NAME=//p' "$env_file" | tail -n 1 | tr -d "'\"")"
project_name="${project_name:-atlas}"
[[ "$project_name" =~ ^[a-z0-9][a-z0-9_-]{0,47}$ ]] || { echo "Invalid ATLAS_PROJECT_NAME in production .env" >&2; exit 1; }
case "$db_mode" in
  docker) compose_args=(-f "$compose_file" -f "$postgres_compose_file") ;;
  external) compose_args=(-f "$compose_file") ;;
  *) echo "Invalid ATLAS_DB_MODE in production .env (expected docker or external)" >&2; exit 1 ;;
esac

# Fail before doing anything if another deploy process is already executing.
exec 9>"$state_dir/deploy.lock"
flock -n 9 || { echo "Another release operation is running" >&2; exit 1; }

compose() {
  # Compose gives exported variables precedence over --env-file. Clear settings
  # from an unrelated shell/stack so preflight and runtime use the same private
  # installation config. Keep only the verified image tags and selected state dir.
  env -u COMPOSE_PROJECT_NAME -u ATLAS_PROJECT_NAME -u ATLAS_NETWORK_PREFIX \
    -u ATLAS_HTTP_PORT -u ATLAS_DB_URL -u ATLAS_DB_USERNAME \
    -u DB_PASSWORD -u ATLAS_DB_PASSWORD -u POSTGRES_PASSWORD -u POSTGRES_ADMIN_PASSWORD -u JWT_SECRET -u STORAGE_ENCRYPTION_KEY \
    -u ATLAS_FLYWAY_BASELINE_ON_MIGRATE -u ATLAS_DEPLOYMENT_ENABLED \
    -u ATLAS_DEPLOYMENT_GITHUB_TOKEN \
    docker compose --project-name "$project_name" --env-file "$env_file" "${compose_args[@]}" "$@"
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

archive_release() {
  local front="$1" back="$2" id record
  validate_tag "$front"
  validate_tag "$back"
  id="${front#git-}_${back#git-}"
  record="$history_dir/$id.env"
  # Don't overwrite original deployment timestamp when returning to an older version.
  if [[ ! -e "$record" ]]; then
    save_manifest "$record" "$front" "$back"
  fi
}

assert_images() {
  docker image inspect "atlas-console:$ATLAS_FRONTEND_TAG" >/dev/null || return 1
  docker image inspect "atlas-backend:$ATLAS_BACKEND_TAG" >/dev/null || return 1
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

if [[ "$operation" == rollback-history ]]; then
  test -f "$current" || { echo "No active version to restore" >&2; exit 1; }
  archive_file="$history_dir/$target_release.env"
  test -f "$archive_file" && ! test -L "$archive_file" || {
    echo "Selected historical release is not present" >&2
    exit 1
  }
  load_manifest "$current"
  old_front="$ATLAS_FRONTEND_TAG"
  old_back="$ATLAS_BACKEND_TAG"
  load_manifest "$archive_file"
  next_front="$ATLAS_FRONTEND_TAG"
  next_back="$ATLAS_BACKEND_TAG"
  [[ "${next_front#git-}_${next_back#git-}" == "$target_release" ]] || {
    echo "Historical release manifest mismatch" >&2
    exit 1
  }
  [[ "$old_front" != "$next_front" || "$old_back" != "$next_back" ]] || {
    echo "Selected release is already running" >&2
    exit 1
  }
  echo "Restoring selected archived application release"
  if ! activate; then
    echo "Historical release health check failed; restoring current release" >&2
    ATLAS_FRONTEND_TAG="$old_front"
    ATLAS_BACKEND_TAG="$old_back"
    export ATLAS_FRONTEND_TAG ATLAS_BACKEND_TAG
    activate || echo "ALERT: recovery of the current release also failed" >&2
    exit 1
  fi
  archive_release "$old_front" "$old_back"
  save_manifest "$previous" "$old_front" "$old_back"
  save_manifest "$current" "$next_front" "$next_back"
  echo "Historical release restored successfully"
  exit 0
fi

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
  archive_release "$old_front" "$old_back"
  archive_release "$next_front" "$next_back"
  save_manifest "$current" "$next_front" "$next_back"
  save_manifest "$previous" "$old_front" "$old_back"
  echo "Rollback completed and previous/current versions swapped"
  exit 0
fi

# Production deployments must use the exact pair the workflow already tested.
# Rollbacks do not build new source and intentionally bypass this check.
test -f "$project_root/backend/pom.xml" || { echo "Monorepo backend directory missing" >&2; exit 1; }
# The bootstrap intentionally leaves its checkout root-owned and group-readable.
# Trust only this exact checkout, never all repositories via safe.directory=*.
front_sha="$(git -c safe.directory="$project_root" -C "$project_root" rev-parse HEAD)"
back_sha="$front_sha"
if [[ ! "${VERIFIED_FRONTEND_SHA:-}" =~ ^[a-f0-9]{40}$ ||
      ! "${VERIFIED_BACKEND_SHA:-}" =~ ^[a-f0-9]{40}$ ||
      "$front_sha" != "$VERIFIED_FRONTEND_SHA" ||
      "$back_sha" != "$VERIFIED_BACKEND_SHA" ||
      "$VERIFIED_FRONTEND_SHA" != "$VERIFIED_BACKEND_SHA" ]]; then
  echo "Refusing deployment: frontend and backend must match the single verified monorepo SHA" >&2
  exit 1
fi
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

# The bundled PostgreSQL image is only built when Atlas owns the database container.
# External/Baota database mode leaves the host-managed database untouched.
if [[ "$db_mode" == docker ]] && ! docker image inspect postgres:17-alpine >/dev/null 2>&1; then
  compose pull postgres
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
  archive_release "$stable_front" "$stable_back"
  save_manifest "$previous" "$stable_front" "$stable_back"
fi
archive_release "git-$front_sha" "git-$back_sha"
save_manifest "$current" "git-$front_sha" "git-$back_sha"
echo "Production application image release recorded successfully"
