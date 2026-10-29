#!/usr/bin/env bash
# Atlas Console first-install bootstrap. Run as root on a fresh Ubuntu/Debian host.
set -Eeuo pipefail
umask 077

REPO="https://github.com/wwxst/atlas-console.git"
REF="main"
INSTALL_ROOT="/opt/atlas"
SOURCE_DIR=""
DB_MODE=""
HTTP_PORT="8088"
NON_INTERACTIVE=false

usage() {
  cat <<'USAGE'
Atlas Console installer

Usage:
  curl -fsSL https://raw.githubusercontent.com/wwxst/atlas-console/main/install.sh | sudo bash
  sudo bash install.sh [--ref main] [--dir /opt/atlas] [--db-mode docker|external] [--port 8088]

Options:
  --ref REF          Git branch or tag to install (default: main)
  --dir PATH         Installation root (default: /opt/atlas)
  --db-mode MODE     docker = install a private MySQL container; external = use an existing MySQL
  --port PORT        Loopback HTTP port for the frontend (default: 8088)
  --non-interactive  Refuse prompts; requires explicit options and existing configuration
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
  if [[ "$NON_INTERACTIVE" == true ]]; then
    [[ -n "$__default" ]] || fail "Non-interactive mode needs a value for $__label."
    printf -v "$__name" '%s' "$__default"
    return
  fi
  read -r -p "$__label [$__default]: " __value < /dev/tty
  printf -v "$__name" '%s' "${__value:-$__default}"
}
valid_port() { [[ "$1" =~ ^[0-9]+$ ]] && (( 10#$1 >= 1024 && 10#$1 <= 65535 )); }

while (($#)); do
  case "$1" in
    --ref) [[ $# -ge 2 ]] || fail "--ref requires a value"; REF="$2"; shift 2 ;;
    --dir) [[ $# -ge 2 ]] || fail "--dir requires a value"; INSTALL_ROOT="$2"; shift 2 ;;
    --db-mode) [[ $# -ge 2 ]] || fail "--db-mode requires a value"; DB_MODE="$2"; shift 2 ;;
    --port) [[ $# -ge 2 ]] || fail "--port requires a value"; HTTP_PORT="$2"; shift 2 ;;
    --non-interactive) NON_INTERACTIVE=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) fail "Unknown option: $1 (use --help)" ;;
  esac
done

[[ "$EUID" -eq 0 ]] || fail "Run as root, for example: curl ... | sudo bash"
[[ "$REF" =~ ^[A-Za-z0-9._/-]+$ && "$REF" != *".."* && "$REF" != /* ]] || fail "Invalid Git ref."
[[ "$INSTALL_ROOT" == /* && "$INSTALL_ROOT" != "/" ]] || fail "--dir must be an absolute non-root path."
valid_port "$HTTP_PORT" || fail "Port must be an integer from 1024 to 65535."

if [[ -z "$DB_MODE" ]]; then
  [[ -r /dev/tty ]] || fail "Interactive setup needs a terminal. Use --non-interactive --db-mode docker for an unattended Docker install."
  if [[ "$NON_INTERACTIVE" == true ]]; then
    fail "--non-interactive requires --db-mode docker or --db-mode external."
  fi
  printf 'Choose the database installation mode:\n'
  printf '  1) Docker-managed MySQL (recommended; no database setup in Baota)\n'
  printf '  2) Existing MySQL (for a database already created in Baota)\n'
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
if [[ "$DB_MODE" == external ]]; then
  [[ -r /dev/tty ]] || fail "External database mode requires a terminal for secure credential entry."
fi
if [[ "$DB_MODE" == external ]]; then
  prompt db_host "MySQL host (same-server Baota users usually configure host.docker.internal)" "host.docker.internal"
  prompt db_port "MySQL port" "3306"
  prompt db_name "Existing empty database name" "web_project"
  prompt db_user "MySQL username" "atlas"
  [[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "MySQL host must be a hostname or IPv4 address."
  valid_port "$db_port" || fail "MySQL port must be from 1024 to 65535."
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || fail "Database name may contain only letters, digits, and underscores."
  [[ "$db_user" =~ ^[A-Za-z0-9_.@-]+$ ]] || fail "Database username contains unsupported characters."
  read -r -s -p 'MySQL password: ' ATLAS_DB_PASSWORD < /dev/tty
  printf '\n'
  [[ -n "$ATLAS_DB_PASSWORD" && "$ATLAS_DB_PASSWORD" != *"'"* && "$ATLAS_DB_PASSWORD" != *
ENV_FILE="$INSTALL_ROOT/.env"
SOURCE_DIR="$INSTALL_ROOT/atlas-console"
RELEASE_DIR="$INSTALL_ROOT/releases"

if [[ -e "$ENV_FILE" || -e "$SOURCE_DIR" ]]; then
  fail "An Atlas installation already exists at $INSTALL_ROOT. This first-install script will not overwrite it; use the documented update/rollback flow."
fi

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
command -v openssl >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "openssl is required; install it and rerun the installer."
  apt-get install -y openssl
}
command -v git >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "git is required to fetch Atlas."
  apt-get install -y git
}

mkdir -p "$INSTALL_ROOT" "$RELEASE_DIR"
chmod 700 "$INSTALL_ROOT" "$RELEASE_DIR"
info "Downloading Atlas Console ($REF)..."
git clone --depth 1 --branch "$REF" "$REPO" "$SOURCE_DIR" || fail "Could not download Atlas from GitHub. Check network access and the branch/tag name."

if [[ "$DB_MODE" == docker ]]; then
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL='jdbc:mysql://mysql:3306/web_project?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
  ATLAS_DB_USERNAME="atlas"
  ATLAS_DB_PASSWORD="$DB_PASSWORD"
else
  prompt db_host "MySQL host (same-server Baota users usually configure host.docker.internal)" "host.docker.internal"
  prompt db_port "MySQL port" "3306"
  prompt db_name "Existing empty database name" "web_project"
  prompt db_user "MySQL username" "atlas"
  [[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "MySQL host must be a hostname or IPv4 address."
  valid_port "$db_port" || fail "MySQL port must be from 1024 to 65535."
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || fail "Database name may contain only letters, digits, and underscores."
  [[ "$db_user" =~ ^[A-Za-z0-9_.@-]+$ ]] || fail "Database username contains unsupported characters."
  if [[ "$NON_INTERACTIVE" == true ]]; then fail "External database credentials must be entered interactively."; fi
  read -r -s -p 'MySQL password: ' ATLAS_DB_PASSWORD < /dev/tty
  printf '\n'
  [[ -n "$ATLAS_DB_PASSWORD" && "$ATLAS_DB_PASSWORD" != *"'"* && "$ATLAS_DB_PASSWORD" != *$'\n'* && "$ATLAS_DB_PASSWORD" != *$'\r'* ]] || fail "Password must be non-empty and cannot contain quotes or newlines."
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL="jdbc:mysql://$db_host:$db_port/$db_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
  ATLAS_DB_USERNAME="$db_user"
fi

MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
STORAGE_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

write_env() {
  local key="$1" value="$2"
  [[ "$value" != *"'"* && "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "Unsafe value for $key."
  printf "%s='%s'\n" "$key" "$value" >> "$ENV_FILE"
}

: > "$ENV_FILE"
write_env ATLAS_DB_MODE "$DB_MODE"
write_env ATLAS_DB_URL "$ATLAS_DB_URL"
write_env ATLAS_DB_USERNAME "$ATLAS_DB_USERNAME"
write_env ATLAS_DB_PASSWORD "$ATLAS_DB_PASSWORD"
write_env MYSQL_ROOT_PASSWORD "$MYSQL_ROOT_PASSWORD"
write_env DB_PASSWORD "$DB_PASSWORD"
write_env JWT_SECRET "$JWT_SECRET"
write_env STORAGE_ENCRYPTION_KEY "$STORAGE_ENCRYPTION_KEY"
write_env ATLAS_FLYWAY_BASELINE_ON_MIGRATE false
write_env ATLAS_HTTP_PORT "$HTTP_PORT"
write_env ATLAS_DEPLOYMENT_ENABLED false
write_env ATLAS_DEPLOYMENT_GITHUB_TOKEN ""
chmod 600 "$ENV_FILE"

if [[ "$DB_MODE" == external ]]; then
  warn "The existing MySQL must be reachable from Docker containers. For same-server Baota MySQL, configure its bind address and grant access only to the Atlas Docker subnet; never expose port 3306 to the public internet."
  warn "Create an EMPTY database before continuing. Flyway will create the schema; do not import web_project.sql."
fi

info "Building and starting Atlas. The first build can take several minutes..."
commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
ATLAS_ENV_FILE="$ENV_FILE" ATLAS_RELEASE_DIR="$RELEASE_DIR" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$SOURCE_DIR/deploy/release.sh" deploy

info "Atlas installation completed."
printf '\n'
printf '  Install directory: %s\n' "$INSTALL_ROOT"
printf '  Config file:       %s/.env (permissions 0600)\n' "$INSTALL_ROOT"
printf '  Local URL:         http://127.0.0.1:%s/\n' "$HTTP_PORT"
printf '\n'
printf 'Next steps:\n'
printf '  1. In Baota, add your domain and create an Nginx reverse proxy to http://127.0.0.1:%s\n' "$HTTP_PORT"
printf '  2. Configure HTTPS in Baota before public access.\n'
printf '  3. Change the default Atlas administrator password before public launch.\n'
printf '  4. Configure a dedicated GitHub Actions Runner before enabling automatic updates.\n'
printf '\n'
printf 'Do not expose MySQL, Redis, or Spring Boot ports directly to the public internet.\n'
\n'* && "$ATLAS_DB_PASSWORD" != *
ENV_FILE="$INSTALL_ROOT/.env"
SOURCE_DIR="$INSTALL_ROOT/atlas-console"
RELEASE_DIR="$INSTALL_ROOT/releases"

if [[ -e "$ENV_FILE" || -e "$SOURCE_DIR" ]]; then
  fail "An Atlas installation already exists at $INSTALL_ROOT. This first-install script will not overwrite it; use the documented update/rollback flow."
fi

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
command -v openssl >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "openssl is required; install it and rerun the installer."
  apt-get install -y openssl
}
command -v git >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "git is required to fetch Atlas."
  apt-get install -y git
}

mkdir -p "$INSTALL_ROOT" "$RELEASE_DIR"
chmod 700 "$INSTALL_ROOT" "$RELEASE_DIR"
info "Downloading Atlas Console ($REF)..."
git clone --depth 1 --branch "$REF" "$REPO" "$SOURCE_DIR" || fail "Could not download Atlas from GitHub. Check network access and the branch/tag name."

if [[ "$DB_MODE" == docker ]]; then
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL='jdbc:mysql://mysql:3306/web_project?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
  ATLAS_DB_USERNAME="atlas"
  ATLAS_DB_PASSWORD="$DB_PASSWORD"
else
  prompt db_host "MySQL host (same-server Baota users usually configure host.docker.internal)" "host.docker.internal"
  prompt db_port "MySQL port" "3306"
  prompt db_name "Existing empty database name" "web_project"
  prompt db_user "MySQL username" "atlas"
  [[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "MySQL host must be a hostname or IPv4 address."
  valid_port "$db_port" || fail "MySQL port must be from 1024 to 65535."
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || fail "Database name may contain only letters, digits, and underscores."
  [[ "$db_user" =~ ^[A-Za-z0-9_.@-]+$ ]] || fail "Database username contains unsupported characters."
  if [[ "$NON_INTERACTIVE" == true ]]; then fail "External database credentials must be entered interactively."; fi
  read -r -s -p 'MySQL password: ' ATLAS_DB_PASSWORD < /dev/tty
  printf '\n'
  [[ -n "$ATLAS_DB_PASSWORD" && "$ATLAS_DB_PASSWORD" != *"'"* && "$ATLAS_DB_PASSWORD" != *$'\n'* && "$ATLAS_DB_PASSWORD" != *$'\r'* ]] || fail "Password must be non-empty and cannot contain quotes or newlines."
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL="jdbc:mysql://$db_host:$db_port/$db_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
  ATLAS_DB_USERNAME="$db_user"
fi

MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
STORAGE_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

write_env() {
  local key="$1" value="$2"
  [[ "$value" != *"'"* && "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "Unsafe value for $key."
  printf "%s='%s'\n" "$key" "$value" >> "$ENV_FILE"
}

: > "$ENV_FILE"
write_env ATLAS_DB_MODE "$DB_MODE"
write_env ATLAS_DB_URL "$ATLAS_DB_URL"
write_env ATLAS_DB_USERNAME "$ATLAS_DB_USERNAME"
write_env ATLAS_DB_PASSWORD "$ATLAS_DB_PASSWORD"
write_env MYSQL_ROOT_PASSWORD "$MYSQL_ROOT_PASSWORD"
write_env DB_PASSWORD "$DB_PASSWORD"
write_env JWT_SECRET "$JWT_SECRET"
write_env STORAGE_ENCRYPTION_KEY "$STORAGE_ENCRYPTION_KEY"
write_env ATLAS_FLYWAY_BASELINE_ON_MIGRATE false
write_env ATLAS_HTTP_PORT "$HTTP_PORT"
write_env ATLAS_DEPLOYMENT_ENABLED false
write_env ATLAS_DEPLOYMENT_GITHUB_TOKEN ""
chmod 600 "$ENV_FILE"

if [[ "$DB_MODE" == external ]]; then
  warn "The existing MySQL must be reachable from Docker containers. For same-server Baota MySQL, configure its bind address and grant access only to the Atlas Docker subnet; never expose port 3306 to the public internet."
  warn "Create an EMPTY database before continuing. Flyway will create the schema; do not import web_project.sql."
fi

info "Building and starting Atlas. The first build can take several minutes..."
commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
ATLAS_ENV_FILE="$ENV_FILE" ATLAS_RELEASE_DIR="$RELEASE_DIR" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$SOURCE_DIR/deploy/release.sh" deploy

info "Atlas installation completed."
printf '\n'
printf '  Install directory: %s\n' "$INSTALL_ROOT"
printf '  Config file:       %s/.env (permissions 0600)\n' "$INSTALL_ROOT"
printf '  Local URL:         http://127.0.0.1:%s/\n' "$HTTP_PORT"
printf '\n'
printf 'Next steps:\n'
printf '  1. In Baota, add your domain and create an Nginx reverse proxy to http://127.0.0.1:%s\n' "$HTTP_PORT"
printf '  2. Configure HTTPS in Baota before public access.\n'
printf '  3. Change the default Atlas administrator password before public launch.\n'
printf '  4. Configure a dedicated GitHub Actions Runner before enabling automatic updates.\n'
printf '\n'
printf 'Do not expose MySQL, Redis, or Spring Boot ports directly to the public internet.\n'
\r'* ]] || fail "Password must be non-empty and cannot contain quotes or newlines."
  ATLAS_DB_URL="jdbc:mysql://$db_host:$db_port/$db_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
  ATLAS_DB_USERNAME="$db_user"
fi

ENV_FILE="$INSTALL_ROOT/.env"
SOURCE_DIR="$INSTALL_ROOT/atlas-console"
RELEASE_DIR="$INSTALL_ROOT/releases"

if [[ -e "$ENV_FILE" || -e "$SOURCE_DIR" ]]; then
  fail "An Atlas installation already exists at $INSTALL_ROOT. This first-install script will not overwrite it; use the documented update/rollback flow."
fi

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
command -v openssl >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "openssl is required; install it and rerun the installer."
  apt-get install -y openssl
}
command -v git >/dev/null 2>&1 || {
  command -v apt-get >/dev/null 2>&1 || fail "git is required to fetch Atlas."
  apt-get install -y git
}

mkdir -p "$INSTALL_ROOT" "$RELEASE_DIR"
chmod 700 "$INSTALL_ROOT" "$RELEASE_DIR"
info "Downloading Atlas Console ($REF)..."
git clone --depth 1 --branch "$REF" "$REPO" "$SOURCE_DIR" || fail "Could not download Atlas from GitHub. Check network access and the branch/tag name."

if [[ "$DB_MODE" == docker ]]; then
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL='jdbc:mysql://mysql:3306/web_project?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
  ATLAS_DB_USERNAME="atlas"
  ATLAS_DB_PASSWORD="$DB_PASSWORD"
else
  prompt db_host "MySQL host (same-server Baota users usually configure host.docker.internal)" "host.docker.internal"
  prompt db_port "MySQL port" "3306"
  prompt db_name "Existing empty database name" "web_project"
  prompt db_user "MySQL username" "atlas"
  [[ "$db_host" =~ ^[A-Za-z0-9.-]+$ ]] || fail "MySQL host must be a hostname or IPv4 address."
  valid_port "$db_port" || fail "MySQL port must be from 1024 to 65535."
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || fail "Database name may contain only letters, digits, and underscores."
  [[ "$db_user" =~ ^[A-Za-z0-9_.@-]+$ ]] || fail "Database username contains unsupported characters."
  if [[ "$NON_INTERACTIVE" == true ]]; then fail "External database credentials must be entered interactively."; fi
  read -r -s -p 'MySQL password: ' ATLAS_DB_PASSWORD < /dev/tty
  printf '\n'
  [[ -n "$ATLAS_DB_PASSWORD" && "$ATLAS_DB_PASSWORD" != *"'"* && "$ATLAS_DB_PASSWORD" != *$'\n'* && "$ATLAS_DB_PASSWORD" != *$'\r'* ]] || fail "Password must be non-empty and cannot contain quotes or newlines."
  DB_PASSWORD="$(openssl rand -hex 32)"
  ATLAS_DB_URL="jdbc:mysql://$db_host:$db_port/$db_name?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
  ATLAS_DB_USERNAME="$db_user"
fi

MYSQL_ROOT_PASSWORD="$(openssl rand -hex 32)"
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
STORAGE_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

write_env() {
  local key="$1" value="$2"
  [[ "$value" != *"'"* && "$value" != *$'\n'* && "$value" != *$'\r'* ]] || fail "Unsafe value for $key."
  printf "%s='%s'\n" "$key" "$value" >> "$ENV_FILE"
}

: > "$ENV_FILE"
write_env ATLAS_DB_MODE "$DB_MODE"
write_env ATLAS_DB_URL "$ATLAS_DB_URL"
write_env ATLAS_DB_USERNAME "$ATLAS_DB_USERNAME"
write_env ATLAS_DB_PASSWORD "$ATLAS_DB_PASSWORD"
write_env MYSQL_ROOT_PASSWORD "$MYSQL_ROOT_PASSWORD"
write_env DB_PASSWORD "$DB_PASSWORD"
write_env JWT_SECRET "$JWT_SECRET"
write_env STORAGE_ENCRYPTION_KEY "$STORAGE_ENCRYPTION_KEY"
write_env ATLAS_FLYWAY_BASELINE_ON_MIGRATE false
write_env ATLAS_HTTP_PORT "$HTTP_PORT"
write_env ATLAS_DEPLOYMENT_ENABLED false
write_env ATLAS_DEPLOYMENT_GITHUB_TOKEN ""
chmod 600 "$ENV_FILE"

if [[ "$DB_MODE" == external ]]; then
  warn "The existing MySQL must be reachable from Docker containers. For same-server Baota MySQL, configure its bind address and grant access only to the Atlas Docker subnet; never expose port 3306 to the public internet."
  warn "Create an EMPTY database before continuing. Flyway will create the schema; do not import web_project.sql."
fi

info "Building and starting Atlas. The first build can take several minutes..."
commit="$(git -C "$SOURCE_DIR" rev-parse HEAD)"
ATLAS_ENV_FILE="$ENV_FILE" ATLAS_RELEASE_DIR="$RELEASE_DIR" \
  VERIFIED_FRONTEND_SHA="$commit" VERIFIED_BACKEND_SHA="$commit" \
  bash "$SOURCE_DIR/deploy/release.sh" deploy

info "Atlas installation completed."
printf '\n'
printf '  Install directory: %s\n' "$INSTALL_ROOT"
printf '  Config file:       %s/.env (permissions 0600)\n' "$INSTALL_ROOT"
printf '  Local URL:         http://127.0.0.1:%s/\n' "$HTTP_PORT"
printf '\n'
printf 'Next steps:\n'
printf '  1. In Baota, add your domain and create an Nginx reverse proxy to http://127.0.0.1:%s\n' "$HTTP_PORT"
printf '  2. Configure HTTPS in Baota before public access.\n'
printf '  3. Change the default Atlas administrator password before public launch.\n'
printf '  4. Configure a dedicated GitHub Actions Runner before enabling automatic updates.\n'
printf '\n'
printf 'Do not expose MySQL, Redis, or Spring Boot ports directly to the public internet.\n'
