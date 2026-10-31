#!/usr/bin/env bash
# Runs only during initialization of a brand-new PostgreSQL data directory.
# Keep the container bootstrap superuser separate from the runtime application role.
set -Eeuo pipefail

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${ATLAS_DB_PASSWORD:?ATLAS_DB_PASSWORD is required}"

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=ON_ERROR_STOP=1 --set=app_password="$ATLAS_DB_PASSWORD" <<'SQL'
CREATE ROLE atlas
    LOGIN
    PASSWORD :'app_password'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS;

ALTER DATABASE web_project OWNER TO atlas;
SQL
