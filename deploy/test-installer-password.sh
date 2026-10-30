#!/usr/bin/env bash
# Execute the installer's actual read command, preserving every password byte.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
read_command="$(grep -m 1 'read -r -s -p.*ATLAS_DB_PASSWORD' "$root/install.sh")"
[[ -n "$read_command" ]] || { echo 'Missing interactive password read command' >&2; exit 1; }
# Substitute only its input descriptor for this portable behavior test.
stdin_command="${read_command% < /dev/tty}"
password=$'  \tsecret \\ $ # \' "\t  '
printf '%s\n' "$password" > "$work/input"
printf '%s' "$password" > "$work/expected"
(
  eval "$stdin_command"
  printf '%s' "$ATLAS_DB_PASSWORD"
) < "$work/input" > "$work/actual"
cmp -s "$work/expected" "$work/actual" || { echo 'Interactive password read changed leading/trailing whitespace or special characters' >&2; exit 1; }

if [[ "$(uname -s)" == Linux ]]; then
  # A controlling PTY verifies the unchanged /dev/tty command as used by curl|bash.
  python3 - "$root/install.sh" "$work" <<'PY'
import os, pathlib, pty, select, signal, sys, time
installer, directory = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
command = next(line for line in installer.read_text().splitlines() if 'read -r -s -p' in line and 'ATLAS_DB_PASSWORD' in line)
expected = (directory / 'expected').read_bytes()
output = directory / 'pty-result'
pid, master = pty.fork()
if pid == 0:
    os.execvp('bash', ['bash', '-c', 'set -e; stty -echo; ' + command + '; printf \'%s\' "$ATLAS_DB_PASSWORD" > "$1"', 'atlas-password-test', str(output)])
reaped = False
try:
    transcript = b''
    deadline = time.monotonic() + 10
    while b'MySQL password: ' not in transcript:
        remaining = deadline - time.monotonic()
        assert remaining > 0, 'Password prompt timed out'
        ready, _, _ = select.select([master], [], [], remaining)
        assert ready, 'Password prompt timed out'
        transcript += os.read(master, 4096)
    os.write(master, expected + b'\n')
    while True:
        finished, status = os.waitpid(pid, os.WNOHANG)
        if finished:
            reaped = True
            assert os.waitstatus_to_exitcode(status) == 0, 'Password prompt child failed'
            break
        assert time.monotonic() < deadline, 'Password read timed out'
        time.sleep(0.02)
    assert output.read_bytes() == expected, 'PTY password read changed password bytes'
finally:
    os.close(master)
    if not reaped:
        try:
            os.kill(pid, signal.SIGKILL)
            os.waitpid(pid, 0)
        except (ProcessLookupError, ChildProcessError):
            pass
print('Real Linux PTY password byte-preservation check passed')
PY
else
  echo 'Linux PTY check skipped on this platform; portable password read behavior passed'
fi
