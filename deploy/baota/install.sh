#!/usr/bin/env bash
# One-time onboarding for BaoTa Nginx + native Spring Boot JAR. No Docker required.
set -Eeuo pipefail

ROOT=/opt/atlas-native
AGENT_USER=atlas-native-update
APP_USER=atlas-native-app
AGENT_UNIT=atlas-native-updater.service
APP_UNIT=atlas-native-backend.service
BACKEND_ENV_FILE=
BOOTSTRAP_TAG=
SKIP_BOOTSTRAP=false
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"

usage() {
  cat <<'HELP'
Usage: sudo bash deploy/baota/install.sh --backend-env-file /root/atlas-backend.env [--tag v1.2.3] [--skip-bootstrap]

BaoTa native deployment requires Linux systemd, Python 3.11+, JDK 25, sudo,
PostgreSQL 17, Redis, and a public GitHub Release containing the native assets.
The frontend directory and JAR are managed under /opt/atlas-native/runtime/current.
BaoTa site root and reverse proxy must be configured separately; this installer
never changes existing websites, Nginx settings, databases, or upload files.
The backend environment file must contain DB_URL, DB_USERNAME, DB_PASSWORD,
REDIS_HOST, JWT_SECRET and STORAGE_ENCRYPTION_KEY, with secure file permissions.
HELP
}

while (($#)); do
  case "$1" in
    --backend-env-file) BACKEND_ENV_FILE="${2:-}"; shift 2 ;;
    --tag) BOOTSTRAP_TAG="${2:-}"; shift 2 ;;
    --skip-bootstrap) SKIP_BOOTSTRAP=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unsupported argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ "$EUID" -eq 0 ]] || { echo "Run as root with sudo" >&2; exit 1; }
[[ -n "$BACKEND_ENV_FILE" && -f "$BACKEND_ENV_FILE" ]] || { echo "A backend environment file is required" >&2; exit 1; }
[[ -z "$BOOTSTRAP_TAG" || "$BOOTSTRAP_TAG" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]] ||
  { echo "Invalid stable release tag" >&2; exit 1; }
for cmd in systemctl useradd runuser python3 java sudo visudo openssl readlink; do
  command -v "$cmd" >/dev/null || { echo "Missing: $cmd" >&2; exit 1; }
done
[[ "$(command -v systemctl)" == "/usr/bin/systemctl" ]] ||
  { echo "This installer requires /usr/bin/systemctl for its fixed sudo policy" >&2; exit 1; }
PYTHON_BIN="$(readlink -f "$(command -v python3)")"
JAVA_BIN="$(readlink -f "$(command -v java)")"
[[ "$PYTHON_BIN" =~ ^/[a-zA-Z0-9_./-]+$ && -x "$PYTHON_BIN" ]] ||
  { echo "Invalid Python executable path" >&2; exit 1; }
[[ "$JAVA_BIN" =~ ^/[a-zA-Z0-9_./-]+$ && -x "$JAVA_BIN" ]] ||
  { echo "Invalid Java executable path" >&2; exit 1; }
[[ "$(python3 -c 'import sys; print(sys.version_info >= (3,11))')" == True ]] ||
  { echo "Python >= 3.11 is required" >&2; exit 1; }
java -version 2>&1 | head -1 | grep -Eq '"25\.' ||
  { echo "JDK 25 is required" >&2; exit 1; }
for key in DB_URL DB_USERNAME DB_PASSWORD REDIS_HOST JWT_SECRET STORAGE_ENCRYPTION_KEY; do
  grep -Eq "^$key=" "$BACKEND_ENV_FILE" ||
    { echo "Missing backend environment key: $key" >&2; exit 1; }
done
if grep -Eq '^ATLAS_DEPLOYMENT_|^UPLOAD_DIR=|^AUTH_CODE_SENDER=|^TRUSTED_PROXY_ADDRESSES=' "$BACKEND_ENV_FILE"; then
  echo "Reserved deployment/upload keys must not be present in the supplied backend env" >&2
  exit 1
fi
if [[ -e "$ROOT/runtime/current" && ! -L "$ROOT/runtime/current" ]]; then
  echo "Refusing to replace unmanaged $ROOT/runtime/current" >&2
  exit 1
fi
if [[ -e /etc/systemd/system/$APP_UNIT || -e /etc/systemd/system/$AGENT_UNIT ]]; then
  echo "Units already installed. Do not overwrite a running customer deployment." >&2
  exit 1
fi
if [[ -e "$ROOT/releases/current.env" ]]; then
  echo "Existing release metadata found; migration must be performed explicitly." >&2
  exit 1
fi
id "$AGENT_USER" >/dev/null 2>&1 ||
  useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin "$AGENT_USER"
id "$APP_USER" >/dev/null 2>&1 ||
  useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin "$APP_USER"

install -d -m 0755 -o root -g root "$ROOT" "$ROOT/agent"
install -d -m 0755 -o "$AGENT_USER" -g "$AGENT_USER" "$ROOT/runtime" "$ROOT/releases"
install -d -m 0750 -o "$APP_USER" -g "$APP_USER" "$ROOT/uploads"
install -d -m 0755 -o root -g root /etc/atlas-native
install -m 0555 -o root -g root "$REPO_ROOT/deploy/updater/server.py" "$ROOT/agent/server.py"
install -m 0555 -o root -g root "$REPO_ROOT/deploy/updater/worker.py" "$ROOT/agent/worker.py"
install -m 0555 -o root -g root "$SCRIPT_DIR/native_agent.py" "$ROOT/agent/native_agent.py"
install -m 0555 -o root -g root "$SCRIPT_DIR/native_worker.py" "$ROOT/agent/native_worker.py"

TOKEN="$(openssl rand -hex 32)"
install -m 0640 -o root -g "$APP_USER" "$BACKEND_ENV_FILE" /etc/atlas-native/backend.env
{
  printf '\nUPLOAD_DIR=%s/uploads\n' "$ROOT"
  printf 'AUTH_CODE_SENDER=provider\n'
  printf 'TRUSTED_PROXY_ADDRESSES=127.0.0.1\n'
  printf 'ATLAS_DEPLOYMENT_ENABLED=true\n'
  printf 'ATLAS_DEPLOYMENT_AGENT_URL=http://127.0.0.1:8090\n'
  printf 'ATLAS_DEPLOYMENT_AGENT_TOKEN=%s\n' "$TOKEN"
  printf 'ATLAS_DEPLOYMENT_STATE_DIR=%s/releases\n' "$ROOT"
} >> /etc/atlas-native/backend.env
{
  printf 'ATLAS_INSTALL_ROOT=%s\n' "$ROOT"
  printf 'ATLAS_RELEASE_DIR=%s/releases\n' "$ROOT"
  printf 'ATLAS_DEPLOYMENT_MODE=baota\n'
  printf 'ATLAS_DEPLOYMENT_AGENT_TOKEN=%s\n' "$TOKEN"
} > /etc/atlas-native/updater.env
chown root:"$AGENT_USER" /etc/atlas-native/updater.env
chmod 0640 /etc/atlas-native/updater.env

cat >"/etc/systemd/system/$APP_UNIT" <<EOF
[Unit]
Description=Atlas Console BaoTa native backend
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_USER
WorkingDirectory=$ROOT
EnvironmentFile=/etc/atlas-native/backend.env
ExecStart=$JAVA_BIN -jar $ROOT/runtime/current/backend.jar --server.address=127.0.0.1 --server.port=8091
Restart=on-failure
RestartSec=4
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=$ROOT/uploads
PrivateTmp=true
ProtectHome=true
UMask=0027

[Install]
WantedBy=multi-user.target
EOF
cat >"/etc/systemd/system/$AGENT_UNIT" <<EOF
[Unit]
Description=Atlas Console BaoTa native updater (loopback only)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$AGENT_USER
Group=$AGENT_USER
WorkingDirectory=$ROOT
EnvironmentFile=/etc/atlas-native/updater.env
ExecStart=$PYTHON_BIN $ROOT/agent/server.py
Restart=on-failure
RestartSec=4
ProtectSystem=strict
ReadWritePaths=$ROOT/runtime $ROOT/releases
ProtectHome=true
PrivateTmp=true
UMask=0027

[Install]
WantedBy=multi-user.target
EOF

# Restrict elevated operations to exactly one fixed service unit.
cat >/etc/sudoers.d/atlas-native-updater <<EOF
$AGENT_USER ALL=(root) NOPASSWD: /usr/bin/systemctl restart $APP_UNIT
EOF
chmod 0440 /etc/sudoers.d/atlas-native-updater
visudo -cf /etc/sudoers.d/atlas-native-updater >/dev/null
systemctl daemon-reload
systemctl enable --now "$AGENT_UNIT"

if [[ "$SKIP_BOOTSTRAP" == false ]]; then
  REQUEST_ID="$(python3 -c 'import uuid; print(uuid.uuid4())')"
  cmd=("$PYTHON_BIN" "$ROOT/agent/native_worker.py" --operation deploy --request-id "$REQUEST_ID")
  [[ -z "$BOOTSTRAP_TAG" ]] || cmd+=(--tag "$BOOTSTRAP_TAG")
  runuser -u "$AGENT_USER" -- env \
    "ATLAS_INSTALL_ROOT=$ROOT" "ATLAS_RELEASE_DIR=$ROOT/releases" \
    "PATH=/usr/local/bin:/usr/bin:/bin" "${cmd[@]}"
  systemctl enable "$APP_UNIT"
fi

cat <<EOF
BaoTa native updater initialized.
  Nginx website root: $ROOT/runtime/current/frontend
  Backend proxy:     http://127.0.0.1:8091
  Updater API:       http://127.0.0.1:8090 (loopback and token protected)
  Release history:   $ROOT/releases
Configure BaoTa Nginx site root + /api, /files and /uploads proxy rules manually.
Do not expose 8090, 8091, 5432, or 6379 to the internet.
EOF
