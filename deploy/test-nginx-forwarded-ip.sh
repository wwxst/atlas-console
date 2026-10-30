#!/usr/bin/env bash
# Real Nginx integration: client IPs, HTTPS scheme and untrusted headers.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
prefix="${ATLAS_TEST_NETWORK_PREFIX:-172.30.249}"
network="atlas-ip-ci-${GITHUB_RUN_ID:-local}-$$-$RANDOM"
frontend="atlas-proxy-ci-$$"
backend="atlas-ip-echo-ci-$$"
tmp="$(mktemp -d)"
created_network=false
created_frontend=false
created_backend=false
cleanup() {
  [[ "$created_frontend" != true ]] || docker rm -f "$frontend" >/dev/null 2>&1 || true
  [[ "$created_backend" != true ]] || docker rm -f "$backend" >/dev/null 2>&1 || true
  [[ "$created_network" != true ]] || docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT

cat > "$tmp/upstream.conf" <<'NGINX'
server {
  listen 8080;
  location / {
    add_header X-Backend-Seen-IP $http_x_forwarded_for always;
    add_header X-Backend-Seen-Scheme $http_x_forwarded_proto always;
    return 204;
  }
}
NGINX

docker network create --subnet "$prefix.0/24" --gateway "$prefix.1" "$network" >/dev/null
created_network=true
docker run -d --name "$backend" --network "$network" --network-alias backend \
  -v "$tmp/upstream.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine >/dev/null
created_backend=true
docker run -d --name "$frontend" --network "$network" --ip "$prefix.10" \
  -p 127.0.0.1::8080 \
  -e ATLAS_NETWORK_GATEWAY="$prefix.1" -e NGINX_ENVSUBST_FILTER='^ATLAS_' \
  -v "$root/deploy/nginx.conf:/etc/nginx/templates/default.conf.template:ro" \
  nginx:1.27-alpine >/dev/null
created_frontend=true
port="$(docker port "$frontend" 8080/tcp | awk -F: 'END {print $NF}')"

for attempts in $(seq 1 20); do
  if curl --fail --silent --output /dev/null "http://127.0.0.1:$port/api/ip-check"; then break; fi
  sleep 1
done

for path in /api/ip-check /files/ip-check; do
  for client_ip in 198.51.100.10 198.51.100.11; do
    for scheme in http https; do
      # Simulate the trusted host edge, which OVERWRITES forwarding headers.
      headers="$(curl --fail --silent --show-error --dump-header - --output /dev/null \
        -H "X-Forwarded-For: $client_ip" -H "X-Forwarded-Proto: $scheme" \
        "http://127.0.0.1:$port$path" | tr -d '\r')"
      grep -Eiq "^X-Backend-Seen-IP: $client_ip$" <<< "$headers"
      grep -Eiq "^X-Backend-Seen-Scheme: $scheme$" <<< "$headers"
    done
  done
  headers="$(curl --fail --silent --show-error --dump-header - --output /dev/null \
    -H 'X-Forwarded-Proto: https, http' "http://127.0.0.1:$port$path" | tr -d '\r')"
  grep -Eiq '^X-Backend-Seen-Scheme: http$' <<< "$headers"
done

# A different container cannot assert a client IP or HTTPS origin.
output="$(docker run --rm --network "$network" --ip "$prefix.20" \
  --entrypoint sh nginx:1.27-alpine -c \
  'wget -S -O /dev/null --header="X-Forwarded-For: 203.0.113.99" --header="X-Forwarded-Proto: https" "http://$1:8080/api/ip-check" 2>&1' \
  sh "$prefix.10")"
printf '%s\n' "$output" | tr -d '\r' | grep -Eiq "X-Backend-Seen-IP: ${prefix//./\\.}\\.20$"
printf '%s\n' "$output" | tr -d '\r' | grep -Eiq 'X-Backend-Seen-Scheme: http$'
echo 'Real Nginx client IP, HTTPS forwarding and spoofing isolation tests passed'
