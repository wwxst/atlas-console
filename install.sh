#!/usr/bin/env bash
# Atlas Console first-install bootstrap. Run as root on a fresh Ubuntu/Debian host.
set -Eeuo pipefail
umask 077

REPO="https://github.com/wwxst/atlas-console.git"
REF="main"
INSTALL_ROOT="/opt/atlas"
PROJECT_NAME="atlas"
NETWORK_PREFIX="172.30.80"
DB_MODE=""
HTTP_PORT="8088"
NON_INTERACTIVE=false

usage() {
  cat <<'USAGE'
Atlas Console installer

Usage:
  curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
  sudo bash install.sh [--ref main] [--db-mode docker|external] [--port 8088]

Options:
  --ref REF          Git branch or tag to install (default: main)
  --db-mode MODE     docker = private PostgreSQL container; external = existing PostgreSQL
  --port PORT        Loopback HTTP port for the frontend (default: 8088)
  --install-dir PATH Absolute, new installation directory (default: /opt/atlas)
  --project-name NAME Unique Compose project and volume namespace (default: atlas)
  --network-prefix PREFIX Private /24 IPv4 prefix (default: 172.30.80)
  --non-interactive  Unattended Docker-managed PostgreSQL install
  --help             Show this help

This script installs the application stack only. It does not install Baota,
configure a public firewall, issue TLS certificates, or register a GitHub Runner.
USAGE
}

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
info() { printf '\n[Atlas] %s\n' "$*"; }
warn() { printf '[Atlas] WARNING: %s\n' "$*" >&2; }
prompt() {
  local __name="$1" __label="$2" __default="$3" __value=""
  read -r -p "$__label [$__default]: " __value < /dev/tty
  printf -v "$__name" '%s' "${__value:-$__default}"
}
valid_port() { [[ "$1" =~ ^[0-9]+$ ]] && (( 10#$1 >= 1024 && 10#$1 <= 65535 )); }

write_env() {
  local key="$1" value="$2"
  [[ "$key" =~ ^[A-Z][A-Z0-9_]*$ ]] || fail "Invalid environment key."
  [[ "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "Newlines are not supported for $key."
  # Compose dotenv double-quoted strings: escape slashes/quotes and suppress
  # interpolation with $$. Never source this file as shell code.
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//\$/\$\$}"
  printf '%s="%s"\n' "$key" "$value" >> "$ENV_FILE"
}

validate_install_options() {
  valid_port "$HTTP_PORT" || fail "Port must be an integer from 1024 to 65535."
  HTTP_PORT=$((10#$HTTP_PORT))
  INSTALL_ROOT="${INSTALL_ROOT%/}"
  [[ "$INSTALL_ROOT" =~ ^/[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+$ &&
     "$INSTALL_ROOT/" != *"/../"* && "$INSTALL_ROOT/" != *"/./"* &&
     "$INSTALL_ROOT" != *"//"* ]] || fail "Use a new absolute installation directory such as /opt/atlas-test."
  [[ "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]{0,47}$ ]] || fail "Project name must contain 1-48 lowercase letters, digits, underscores or hyphens, starting with a letter or digit."
  [[ "$NETWORK_PREFIX" =~ ^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]] || fail "Use a private IPv4 prefix such as 172.30.81."
  local first second third
  IFS=. read -r first second third <<< "$NETWORK_PREFIX"
  (( second <= 255 && third <= 255 )) || fail "Invalid IPv4 prefix."
  (( first == 10 || (first == 172 && second >= 16 && second <= 31) ||
     (first == 192 && second == 168) )) || fail "The installation network must use a private IPv4 /24."
}

cidr_overlaps_install() {
  local cidr="$1" a b c d bits target mask address
  [[ "$cidr" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+(/[0-9]+)?$ ]] || return 1
  bits="${cidr#*/}"
  [[ "$cidr" == */* ]] || bits=32
  (( bits >= 0 && bits <= 32 )) || return 1
  IFS=. read -r a b c d <<< "${cidr%/*}"
  address=$(( (10#$a << 24) | (10#$b << 16) | (10#$c << 8) | 10#$d ))
  IFS=. read -r a b c <<< "$NETWORK_PREFIX"
  target=$(( (10#$a << 24) | (10#$b << 16) | (10#$c << 8) ))
  (( bits <= 24 )) || bits=24
  mask=$(( (0xffffffff << (32-bits)) & 0xffffffff ))
  (( (address & mask) == (target & mask) ))
}

preflight_install() {
  [[ ! -e "$INSTALL_ROOT" && ! -L "$INSTALL_ROOT" ]] || fail "Installation directory already exists: $INSTALL_ROOT. Nothing was changed. Retry the existing release as atlas-deploy; do not delete data or volumes."
  command -v ss >/dev/null 2>&1 || fail "ss (iproute2) is required to check the HTTP port safely. Install iproute2 and rerun."
  local listeners resource networks ranges cidr
  listeners="$(ss -H -ltn)" || fail "Could not inspect listening ports."
  if awk -v port="$HTTP_PORT" '$4 ~ (":" port "$") {found=1} END {exit !found}' <<< "$listeners"; then
    fail "HTTP port $HTTP_PORT is already listening. Choose another --port."
  fi
  if command -v ip >/dev/null 2>&1; then
    ranges="$(ip -4 route show)" || fail "Could not inspect host IPv4 routes."
    while read -r cidr _; do
      [[ "$cidr" != default ]] || continue
      if cidr_overlaps_install "$cidr"; then fail "Network $NETWORK_PREFIX.0/24 overlaps host route $cidr. Choose another --network-prefix."; fi
    done <<< "$ranges"
  fi
  if command -v docker >/dev/null 2>&1; then
    docker info >/dev/null 2>&1 || fail "Docker is installed but unavailable. Start it, then rerun the read-only preflight."
    resource="$(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT_NAME")" || fail "Could not inspect Docker containers."
    [[ -z "$resource" ]] || fail "Compose project $PROJECT_NAME already has containers. Choose another --project-name; existing data is untouched."
    resource="$(docker volume ls -q --filter "label=com.docker.compose.project=$PROJECT_NAME")" || fail "Could not inspect Docker volumes."
    [[ -z "$resource" ]] || fail "Compose project $PROJECT_NAME already has volumes. Choose another --project-name; existing data is untouched."
    resource="$(docker volume ls --format '{{.Name}}')" || fail "Could not inspect Docker volume names."
    while read -r resource; do
      case "$resource" in
        "${PROJECT_NAME}_postgres_data"|"${PROJECT_NAME}_redis_data"|"${PROJECT_NAME}_uploads_data")
          fail "Volume $resource already exists, including unlabelled data. Choose another --project-name." ;;
      esac
    done <<< "$resource"
    resource="$(docker network ls -q --filter "label=com.docker.compose.project=$PROJECT_NAME")" || fail "Could not inspect Docker project networks."
    [[ -z "$resource" ]] || fail "Compose project $PROJECT_NAME already has networks. Choose another --project-name."
    resource="$(docker network ls --format '{{.Name}}')" || fail "Could not inspect Docker network names."
    while read -r resource; do
      [[ "$resource" != "${PROJECT_NAME}_default" ]] || fail "Network $resource already exists. Choose another --project-name."
    done <<< "$resource"
    networks="$(docker network ls -q)" || fail "Could not inspect Docker networks."
    if [[ -n "$networks" ]]; then
      # IDs originate only from Docker, not user input.
      ranges="$(docker network inspect --format '{{range .IPAM.Config}}{{println .Subnet}}{{end}}' $networks)" || fail "Could not inspect Docker network ranges."
      while read -r cidr; do
        if cidr_overlaps_install "$cidr"; then fail "Network $NETWORK_PREFIX.0/24 overlaps Docker network $cidr. Choose another --network-prefix."; fi
      done <<< "$ranges"
    fi
  fi
}

atlas_installer_main() {
while (($#)); do
  case "$1" in
    --ref) [[ $# -ge 2 ]] || fail "--ref requires a value"; REF="$2"; shift 2 ;;
    --db-mode) [[ $# -ge 2 ]] || fail "--db-mode requires a value"; DB_MODE="$2"; shift 2 ;;
    --port) [[ $# -ge 2 ]] || fail "--port requires a value"; HTTP_PORT="$2"; shift 2 ;;
    --install-dir) [[ $# -ge 2 ]] || fail "--install-dir requires a value"; INSTALL_ROOT="$2"; shift 2 ;;
    --project-name) [[ $# -ge 2 ]] || fail "--project-name requires a value"; PROJECT_NAME="$2"; shift 2 ;;
    --network-prefix) [[ $# -ge 2 ]] || fail "--network-prefix requires a value"; NETWORK_PREFIX="$2"; shift 2 ;;
    --non-interactive) NON_INTERACTIVE=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) fail "Unknown option: $1 (use --help)" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || fail "Run as root, for example: curl ... | sudo bash"
[[ "$REF" =~ ^[A-Za-z0-9._/-]+$ && "$REF" != *".."* && "$REF" != /* ]] || fail "Invalid Git ref."
validate_install_options
# Inspect existing resources before credentials, package installation or file writes.
preflight_install

if [[ -z "$DB_MODE" ]]; then
  [[ "$NON_INTERACTIVE" != true ]] || fail "--non-interactive requires --db-mode docker."
  [[ -r /dev/tty ]] || fail "Interactive setup needs a terminal. Use --non-interactive --db-mode docker for unattended Docker install."
  printf 'Choose the database installation mode:\n'
  printf '  1) Docker-managed PostgreSQL (recommended; no database setup in Baota)\n'
  printf '  2) Existing PostgreSQL (for a database already created in Baota)\n'
  read -r -p 'Select [1]: ' db_choice < /dev/tty
  case "${db_choice:-1}" in
    1) DB_MODE="docker" ;;
    2) DB_MODE="external" ;;
    *) fail "Please select 1 or 2." ;;
  esac
fi
[[ "$DB_MODE" == docker || "$DB_MODE" == external ]] || fail "--db-mode must be docker or external."
if [[ "$NON_INTERACTIVE" == true && "$DB_MODE" == external ]]; then
  fail "External database mode needs interactive credentials; use the interactive installer."
fi

# Collect and validate external database credentials before installing packages or downloading code.
if [[ "$DB_MODE" == external ]]; then
  [[ -r /dev/tty ]] || fail "External database mode requires a terminal for secure credential entry."
  prompt db_host "PostgreSQL host (same-server Baota users usually configure host.docker.internal)" "host.docker.internal"
  prompt db_port "PostgreSQL port" "5432"
  prompt db_name "Existing empty database name" "web_project"
  prompt db_user "PostgreSQL username" "atlas"
  [[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "PostgreSQL host must be a hostname or IPv4 address."
  [[ "$db_host" != localhost && "$db_host" != 127.0.0.1 ]] || fail "Inside Docker, localhost is the container itself. Use host.docker.internal for same-server Baota PostgreSQL, after configuring a restricted PostgreSQL listener."
  valid_port "$db_port" || fail "PostgreSQL port must be from 1024 to 65535."
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || fail "Database name may contain only letters, digits, and underscores."
  [[ "$db_user" =~ ^[A-Za-z0-9_.@-]+$ ]] || fail "Database username contains unsupported characters."
  IFS= read -r -s -p 'PostgreSQL password: ' ATLAS_DB_PASSWORD < /dev/tty
  printf '\n'
  [[ -n "$ATLAS_DB_PASSWORD" && "$ATLAS_DB_PASSWORD" != *$'\n'* && "$ATLAS_DB_PASSWORD" != *$'\r'* ]] || fail "Password must be non-empty and cannot contain newlines."
  ATLAS_DB_URL="jdbc:postgresql://$db_host:$db_port/$db_name"
  ATLAS_DB_USERNAME="$db_user"
fi

ENV_FILE="$INSTALL_ROOT/.env"
SOURCE_DIR="$INSTALL_ROOT/atlas-console"
RELEASE_DIR="$INSTALL_ROOT/releases"
command -v curl >/dev/null 2>&1 || fail "curl is required. Install it and rerun the installer."

install_docker() {
  if command -v docker >/dev/null 2>&1; then
    if docker compose version >/dev/null 2>&1; then
      systemctl enable --now docker >/dev/null 2>&1 || true
      docker info >/dev/null 2>&1 || fail "Docker is installed but not running. Start Docker and rerun."
      return
    fi
    if command -v apt-get >/dev/null 2>&1; then
      apt-get update -y
      apt-get install -y docker-compose-plugin || fail "Docker exists but Compose v2 is missing. Install the Docker Compose v2 plugin and rerun."
      docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is still unavailable."
      systemctl enable --now docker >/dev/null 2>&1 || true
      return
    fi
    fail "Docker Compose v2 is required. Install it using your OS package manager and rerun."
  fi

  [[ -r /etc/os-release ]] || fail "Cannot identify this Linux distribution."
  . /etc/os-release
  [[ "$ID" == ubuntu || "$ID" == debian ]] || fail "Automatic Docker installation currently supports Ubuntu and Debian. Install Docker Engine + Compose v2 manually, then rerun."
  command -v apt-get >/dev/null 2>&1 || fail "apt-get is required to install Docker."

  info "Installing Docker Engine and Compose v2 from the official Docker APT repository..."
  apt-get update -y
  apt-get install -y ca-certificates curl git openssl gnupg
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL "https://download.docker.com/linux/$ID/gpg" -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  local arch codename
  arch="$(dpkg --print-architecture)"
  codename="${VERSION_CODENAME:-}"
  [[ -n "$codename" ]] || fail "Cannot determine the OS release codename."
  printf 'Types: deb\nURIs: https://download.docker.com/linux/%s\nSuites: %s\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' "$ID" "$codename" "$arch" > /etc/apt/sources.list.d/docker.sources
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
  docker info >/dev/null 2>&1 || fail "Docker installation completed but the daemon is not ready."
}

install_docker

# A dedicated non-root account will run the future GitHub Actions Runner.
# Membership in the Docker group is effectively root access, so keep it dedicated.
getent group atlas-deploy >/dev/null 2>&1 || groupadd --system atlas-deploy
id atlas-deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash --gid atlas-deploy atlas-deploy
getent group docker >/dev/null 2>&1 || groupadd docker
usermod -aG atlas-deploy,docker atlas-deploy
command -v runuser >/dev/null 2>&1 || fail "runuser is required to run the initial deployment as atlas-deploy."

command -v openssl >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "openssl is required; install it and rerun the installer."
  apt-get install -y openssl
}
command -v git >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "git is required to fetch Atlas."
  apt-get install -y git
}

mkdir -p "$INSTALL_ROOT" "$RELEASE_DIR"
chown root:atlas-deploy "$INSTALL_ROOT"
chmod 750 "$INSTALL_ROOT"
chown atlas-deploy:atlas-deploy "$RELEASE_DIR"
chmod 700 "$RELEASE_DIR"
info "Downloading Atlas Console ($REF)..."
if ! git clone --depth 1 --branch "$REF" "$REPO" "$SOURCE_DIR"; then
  rm -rf -- "$SOURCE_DIR"
  fail "Could not download Atlas from GitHub. Check network access and the branch/tag name."
fi
chown -R root:atlas-deploy "$SOURCE_DIR"
find "$SOURCE_DIR" -type d -exec chmod 750 {} +
find "$SOURCE_DIR" -type f -exec chmod g+r {} +

if [[ "$DB_MODE" == docker ]]; then
  ATLAS_DB_PASSWORD="$(openssl rand -hex 32)"
  POSTGRES_ADMIN_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL='jdbc:postgresql://postgres:5432/web_project'
  ATLAS_DB_USERNAME="atlas"
else
  POSTGRES_ADMIN_PASSWORD=""
fi

JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
STORAGE_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

: > "$ENV_FILE"
write_env ATLAS_PROJECT_NAME "$PROJECT_NAME"
write_env ATLAS_NETWORK_PREFIX "$NETWORK_PREFIX"
write_env ATLAS_RELEASE_DIR "$RELEASE_DIR"
write_env ATLAS_DB_MODE "$DB_MODE"
write_env ATLAS_DB_URL "$ATLAS_DB_URL"
write_env ATLAS_DB_USERNAME "$ATLAS_DB_USERNAME"
write_env ATLAS_DB_PASSWORD "$ATLAS_DB_PASSWORD"
if [[ "$DB_MODE" == docker ]]; then write_env POSTGRES_ADMIN_PASSWORD "$POSTGRES_ADMIN_PASSWORD"; fi
write_env JWT_SECRET "$JWT_SECRET"
write_env STORAGE_ENCRYPTION_KEY "$STORAGE_ENCRYPTION_KEY"
write_env ATLAS_FLYWAY_BASELINE_ON_MIGRATE false
write_env ATLAS_HTTP_PORT "$HTTP_PORT"
write_env ATLAS_DEPLOYMENT_ENABLED false
write_env ATLAS_DEPLOYMENT_GITHUB_TOKEN ""
chown root:atlas-deploy "$ENV_FILE"
chmod 640 "$ENV_FILE"

if [[ "$DB_MODE" == external ]]; then
  warn "The existing PostgreSQL must be reachable from Docker containers. For same-server Baota PostgreSQL, configure its bind address and grant access only to the Atlas Docker subnet; never expose port 5432 to the public internet."
  warn "Create an EMPTY database before continuing. Flyway will create the schema; do not import web_project.sql."
fi

info "Building and starting Atlas. The first build can take several minutes..."
commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
runuser -u atlas-deploy -- env ATLAS_ENV_FILE="$ENV_FILE" ATLAS_RELEASE_DIR="$RELEASE_DIR" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$SOURCE_DIR/deploy/release.sh" deploy

info "Atlas installation completed."
printf '\n'
printf '  Install directory: %s\n' "$INSTALL_ROOT"
printf '  Config file:       %s/.env (root:atlas-deploy, permissions 0640)\n' "$INSTALL_ROOT"
printf '  Local URL:         http://127.0.0.1:%s/\n' "$HTTP_PORT"
printf '\n'
printf 'Next steps:\n'
printf '  1. In Baota, add your domain and create an Nginx reverse proxy to http://127.0.0.1:%s\n' "$HTTP_PORT"
printf '  2. Configure HTTPS in Baota before public access.\n'
printf '  3. Change the default Atlas administrator password before public launch.\n'
printf '  4. Configure a dedicated GitHub Actions Runner before enabling automatic updates.\n'
printf '\n'
printf 'Do not expose PostgreSQL, Redis, or Spring Boot ports directly to the public internet.\n'
}

if [[ -z "${BASH_SOURCE[0]:-}" || "${BASH_SOURCE[0]}" == "$0" ]]; then
  atlas_installer_main "$@"
fi
