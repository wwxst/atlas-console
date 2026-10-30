#!/usr/bin/env bash
# Integration test with real Nginx (frontend + upstream) and two external IPs.
set -Eeuo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
network="atlas-ip-ci-${GITHUB_RUN_ID:-local}-$$"
frontend="atlas-proxy-ci-$$"
backend="atlas-ip-echo-ci-$$"
tmp="$(mktemp -d)"
cleanup() {
  docker rm -f "$frontend" "$backend" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT

cat > "$tmp/upstream.conf" <<'NGINX'
server {
  listen 8080;
  location / {
    add_header X-Backend-Seen-IP $http_x_forwarded_for always;
    return 204;
  }
}
NGINX

docker network create --subnet 172.30.80.0/24 --gateway 172.30.80.1 "$network"
docker run -d --name "$backend" --network "$network" --network-alias backend \
  -v "$tmp/upstream.conf:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine >/dev/null
docker run -d --name "$frontend" --network "$network" --ip 172.30.80.10 \
  -p 127.0.0.1:18088:8080 \
  -v "$repo_root/frontend/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
  nginx:1.27-alpine >/dev/null

for attempts in $(seq 1 20); do
  if curl --silent --output /dev/null "http://127.0.0.1:18088/api/ip-check"; then
    break
  fi
  sleep 1
done

for client_ip in 198.51.100.10 198.51.100.11; do
  # Simulate the trusted host TLS edge, which OVERWRITES X-Forwarded-For.
  seen="$(curl --silent --show-error --dump-header - --output /dev/null \
    -H "X-Forwarded-For: $client_ip" http://127.0.0.1:18088/api/ip-check \
    | tr -d '\r' | grep -i '^X-Backend-Seen-IP:' | awk '{print $2}')"
  [[ "$seen" == "$client_ip" ]] || {
    echo "Expected separate IP $client_ip, received $seen" >&2
    exit 1
  }
done

# A container which is NOT the trusted host gateway cannot spoof the IP
# through X-Forwarded-For. Nginx overwrites the supplied forged header.
output="$(docker run --rm --network "$network" --ip 172.30.80.20 \
  --entrypoint sh nginx:1.27-alpine -c \
  'wget -S -O /dev/null --header="X-Forwarded-For: 203.0.113.99" http://172.30.80.10:8080/api/ip-check 2>&1')"
printf '%s\n' "$output" | tr -d '\r' | grep -Eiq 'X-Backend-Seen-IP: 172\.30\.80\.20$' || {
  echo "Nginx accepted an untrusted forwarded IP" >&2
  echo "$output" >&2
  exit 1
}

echo "Nginx client IP forwarding integration test passed"
