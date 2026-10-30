#!/usr/bin/env bash
# A root-launched runuser keeps /root as cwd; the release must enter its checkout.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/fixture/deploy" "$work/blocked"
chmod 755 "$work" "$work/fixture" "$work/fixture/deploy"
chmod 700 "$work/blocked"
fixture="$work/fixture/deploy/release.sh"
printf '#!/usr/bin/env bash\nset -Eeuo pipefail\n' > "$fixture"
# Execute the real release entry sequence in a readable fixture checkout.
sed -n '/^project_root=/,/^compose_file=/p' "$root/deploy/release.sh" >> "$fixture"
cat >> "$fixture" <<'SH'
[[ "$PWD" == "$project_root" ]] || { echo 'Release kept the invoking directory instead of its checkout' >&2; exit 1; }
SH
chmod 755 "$fixture"
(cd "$work/blocked" && bash "$fixture")
if [[ "$(uname -s)" == Linux && "$EUID" == 0 ]] && command -v runuser >/dev/null 2>&1 && id nobody >/dev/null 2>&1; then
  (cd "$work/blocked" && runuser -u nobody -- bash "$fixture")
  echo 'Real Linux runuser release cwd check passed from an unreadable directory'
else
  echo 'Release cwd behavior passed; root-only unreadable-cwd check skipped'
fi
