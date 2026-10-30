#!/usr/bin/env bash
set -euo pipefail
repo="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
image="atlas-frontend-assets-test:$$"
container="atlas-frontend-assets-test-$$"
container_id=""
cleanup() {
  if [[ -n "$container_id" ]]; then docker rm -f "$container_id" >/dev/null 2>&1 || true; fi
  rm -rf "$work"
}
trap cleanup EXIT
git -C "$repo" archive HEAD | tar -x -C "$work"
cp "$repo/frontend/Dockerfile" "$work/frontend/Dockerfile"
# Installer checkout permissions are private. Public assets must still be served
# by the image's unprivileged Nginx worker, independently of checkout umask.
find "$work/frontend/public" -type f -exec chmod 0640 {} +
docker build -t "$image" "$work/frontend"
container_id="$(docker create --name "$container" --network none "$image")"
docker start "$container_id" >/dev/null
ready=false
for _ in {1..30}; do
  if docker exec "$container" wget -q -O /dev/null http://127.0.0.1:8080/; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]]
for asset in favicon.svg icons.svg; do
  docker exec "$container" wget -q -O /dev/null "http://127.0.0.1:8080/$asset"
done
echo 'Real production Nginx serves public assets from a restrictive checkout'
