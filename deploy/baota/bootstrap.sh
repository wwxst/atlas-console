#!/usr/bin/env bash
# Retry or finish the initial BaoTa native deployment after the agent is installed.
set -Eeuo pipefail
ROOT=/opt/atlas-native
AGENT_USER=atlas-native-update
TAG=
while (($#)); do
  case "$1" in
    --tag) TAG="${2:-}"; shift 2 ;;
    -h|--help)
      echo "Usage: sudo bash deploy/baota/bootstrap.sh [--tag v1.2.3]"
      exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ "$EUID" == 0 ]] || { echo "Run with sudo" >&2; exit 1; }
[[ -z "$TAG" || "$TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] ||
  { echo "Invalid stable release tag" >&2; exit 1; }
[[ -f "$ROOT/agent/native_worker.py" && -f /etc/atlas-native/updater.env ]] ||
  { echo "BaoTa native updater is not installed" >&2; exit 1; }
[[ ! -e "$ROOT/releases/current.env" ]] ||
  { echo "An active application release already exists; use the admin update panel" >&2; exit 1; }

PYTHON_BIN="$(readlink -f "$(command -v python3)")"
REQUEST_ID="$("$PYTHON_BIN" -c 'import uuid; print(uuid.uuid4())')"
cmd=("$PYTHON_BIN" "$ROOT/agent/native_worker.py" --operation deploy --request-id "$REQUEST_ID")
[[ -z "$TAG" ]] || cmd+=(--tag "$TAG")
runuser -u "$AGENT_USER" -- env \
  "ATLAS_INSTALL_ROOT=$ROOT" "ATLAS_RELEASE_DIR=$ROOT/releases" \
  "PATH=/usr/local/bin:/usr/bin:/bin" "${cmd[@]}"
systemctl enable atlas-native-backend.service
echo "Native bootstrap succeeded. Configure BaoTa Nginx site root at $ROOT/runtime/current/frontend."
