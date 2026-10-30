#!/usr/bin/env bash
# Unit/behavior checks; Docker and listeners below are explicit test doubles.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
help="$(bash "$root/install.sh" --help)"
for flag in '--install-dir PATH' '--project-name NAME' '--network-prefix PREFIX'; do
  grep -q -- "$flag" <<< "$help" || { echo "Missing isolation option: $flag" >&2; exit 1; }
done
# Sourcing exposes pure validation/preflight functions without running setup.
source "$root/install.sh"
INSTALL_ROOT=/opt/atlas-unit-test
PROJECT_NAME=atlas-unit-test
NETWORK_PREFIX=172.30.91
HTTP_PORT=18091
validate_install_options
HTTP_PORT=018091
validate_install_options
[[ "$HTTP_PORT" == 18091 ]] || { echo 'HTTP port was not normalized before conflict checks' >&2; exit 1; }
for NETWORK_PREFIX in 127.0.0 172.32.0 192.168.256 10.1 10.01.1; do
  if (validate_install_options >/dev/null 2>&1); then
    echo "Unsafe network prefix accepted" >&2; exit 1
  fi
done
NETWORK_PREFIX=172.30.91
for INSTALL_ROOT in / /opt relative /opt/../etc; do
  if (validate_install_options >/dev/null 2>&1); then
    echo 'Unsafe installation directory accepted' >&2; exit 1
  fi
done
INSTALL_ROOT=/opt/atlas-unit-test
for PROJECT_NAME in Atlas '../atlas' '-atlas'; do
  if (validate_install_options >/dev/null 2>&1); then
    echo 'Unsafe Compose project name accepted' >&2; exit 1
  fi
done
PROJECT_NAME=atlas-unit-test
for cidr in 172.30.91.0/24 172.30.0.0/16 172.30.91.10/32 0.0.0.0/0; do
  cidr_overlaps_install "$cidr" || { echo 'Missed overlapping route' >&2; exit 1; }
done
for cidr in 172.30.90.0/24 10.0.0.0/8 192.168.1.0/24; do
  if cidr_overlaps_install "$cidr"; then echo 'Disjoint route rejected' >&2; exit 1; fi
done
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
INSTALL_ROOT="$work/install"
# Fake only the external inspection boundary; assert real preflight decisions.
ss() { [[ "${TEST_CONFLICT:-}" != port ]] || echo "LISTEN 0 128 0.0.0.0:$HTTP_PORT 0.0.0.0:*"; return 0; }
ip() { echo 'default via 192.0.2.1 dev eth0'; }
docker() {
  case "$*" in
    info) return 0 ;;
    'ps -aq '*) [[ "${TEST_CONFLICT:-}" != container ]] || echo existing-container ;;
    'volume ls -q '*) [[ "${TEST_CONFLICT:-}" != volume ]] || echo existing-volume ;;
    'volume ls --format '*) [[ "${TEST_CONFLICT:-}" != unlabelled-volume ]] || echo "${PROJECT_NAME}_mysql_data" ;;
    'network ls -q --filter '*) [[ "${TEST_CONFLICT:-}" != project-network ]] || echo existing-network ;;
    'network ls --format '*) [[ "${TEST_CONFLICT:-}" != unlabelled-network ]] || echo "${PROJECT_NAME}_default" ;;
    'network ls -q') echo other-network ;;
    'network inspect '*) [[ "${TEST_CONFLICT:-}" != network ]] || echo 172.30.91.0/24 ;;
    *) echo "Unexpected Docker mutation/command: $*" >&2; exit 81 ;;
  esac
  return 0
}
preflight_install
for TEST_CONFLICT in port container volume project-network network unlabelled-volume unlabelled-network; do
  if (preflight_install >/dev/null 2>&1); then
    echo "Preflight missed $TEST_CONFLICT conflict" >&2; exit 1
  fi
  test ! -e "$INSTALL_ROOT" || { echo 'Preflight wrote installation files' >&2; exit 1; }
done
unset TEST_CONFLICT
mkdir "$INSTALL_ROOT"
printf 'existing data' > "$INSTALL_ROOT/.env"
before="$(cat "$INSTALL_ROOT/.env")"
if (preflight_install >/dev/null 2>&1); then echo 'Existing install accepted' >&2; exit 1; fi
[[ "$(cat "$INSTALL_ROOT/.env")" == "$before" ]]
echo 'Installer argument and read-only preflight behavior checks passed (test doubles)'
