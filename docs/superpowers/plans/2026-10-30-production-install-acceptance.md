# Atlas production installation acceptance plan

**Goal:** Evaluate main `654bffd77ce4b71a949344607d361db3b1f269ba` on the provided Ubuntu 22.04/Baota server, repair demonstrated defects through a PR, and publish an evidence-based acceptance matrix.

**Architecture:** Preserve the original main baseline and existing server state. Use independent install directories, Compose projects, networks, database schemas/users, and persistent volumes for each installation scenario; never remove databases or volumes. Publish only a Baota HTTPS frontend. Credentials and raw operational artifacts remain outside Git.

**Stack:** Bash, Docker Engine/Compose, MySQL, Redis, JDK 25/Spring Boot/Flyway, React/Nginx, Baota Nginx, real browser.

## Execution checklist

- [x] Fetch origin/main; record SHA and create an isolated worktree/branch.
- [x] Inventory server listeners, applications, storage, Docker, and Baota components before changes.
- [x] Run the original interactive Docker installer and record its exact failure or success.
- [x] Run existing installer/release tests on Linux; keep environmental Windows limitations separate from product defects.
- [x] For each demonstrated defect, add a failing regression test, apply the smallest correction, and rerun relevant tests.
- [x] Install missing Baota Nginx/MySQL components without changing existing application data; bind MySQL only to a private interface and restrict user grants to the test network.
- [x] Exercise interactive Docker, interactive external Baota MySQL, and unattended Docker installation in independent clean environments.
- [x] Verify image builds, database readiness, Flyway history, Redis, Spring Boot health, frontend assets, and unauthenticated/authenticated APIs for each mode.
- [x] Exercise special-character database passwords, failure/retry, duplicate-install preservation, and occupied-port rejection.
- [x] Configure an isolated Baota Nginx HTTPS IP site, verify paths, forwarding headers, same-origin API calls, and client-IP spoof protection.
- [x] Use a real browser for login, every implemented menu/page, refresh, logout and relogin; record unavailable external integrations explicitly.
- [x] Write persistent sentinels for each test environment, reboot the server, and verify auto-start and sentinel preservation for every mode.
- [x] Review final changes against safety and implementation requirements; run appropriate regression checks and `git diff --check`.
- [x] Commit/push only the acceptance branch, open a PR, and deliver a report separating baseline failures, verified fixes, failures, and untested items.

## Evidence and safety rules

Remote raw evidence: `/root/atlas-acceptance-20261030/evidence` (root only). Public report: `docs/acceptance/2026-10-30-production-installation.md`.

Never run `docker compose down -v`, `docker volume rm`, database deletion, global prune, destructive reset, force-push, direct main write, or schema baseline on an unverified existing database. No MySQL/Redis/backend host-public port bindings. Preserve failed-install state for recovery tests. Record the exact revision used by every build. No domain was supplied; HTTPS is evaluated using the server IP and an IP certificate.

The desktop browser control surface could not connect; real isolated headless Chromium sessions provide browser evidence. Shell/API probes provide separately recorded protocol and persistence evidence. Simulated tests do not count as real service acceptance. A missing prerequisite or unavailable integration must remain marked untested/blocked rather than pass.
