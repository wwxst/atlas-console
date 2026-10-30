#!/usr/bin/env bash
# Exercise the release script's actual Git command against a differently-owned
# checkout (Git's test switch), without changing any global trust configuration.
set -Eeuo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git init -q "$work/checkout"
git -C "$work/checkout" -c user.name=Atlas-Test -c user.email=atlas-test@example.invalid commit -q --allow-empty -m fixture
expected="$(git -C "$work/checkout" rev-parse HEAD)"
project_root="$(git -C "$work/checkout" rev-parse --show-toplevel)"
export GIT_TEST_ASSUME_DIFFERENT_OWNER=1
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=safe.directory GIT_CONFIG_VALUE_0=
if git -C "$project_root" rev-parse HEAD >/dev/null 2>&1; then
  echo 'Git ownership fixture did not reject the untrusted checkout' >&2; exit 1
fi
revision_line="$(sed -n '/^front_sha=/p' "${ATLAS_RELEASE_SCRIPT:-$root/deploy/release.sh}")"
[[ -n "$revision_line" ]] || { echo 'Missing release revision command' >&2; exit 1; }
eval "$revision_line"
[[ "$front_sha" == "$expected" ]]
echo 'Exact-checkout Git ownership regression passed'
