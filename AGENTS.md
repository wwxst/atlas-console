# Atlas Console repository instructions

This repository is a single Git monorepo. The frontend and backend are **sibling projects**, not nested projects.

## Project boundaries

- `frontend/`: React, TypeScript, Vite, browser UI, frontend unit tests, and Playwright E2E tests.
- `backend/`: Spring Boot application, Maven build, backend tests, and Flyway database migrations.
- `deploy/`: Docker Compose, first-install bootstrap, release/rollback scripts, and deployment regression tests.
- `docs/`: project-wide architecture, UI, engineering, and security decisions.
- `.github/workflows/`: frontend/backend verification and production deployment.
- `.agents/skills/`: shared Codex skills.

Do not move frontend source or package files back to the repository root, and do not place backend files under `frontend/`.

## Before changing code

- For frontend work, read `docs/architecture.md`, `docs/ui-system.md`, `docs/engineering.md`, and `.agents/skills/atlas-frontend/SKILL.md`.
- For backend work, read `backend/AGENTS.md` and the relevant documents under `backend/docs/`.
- For installation or production deployment work, read `deploy/installer.md` and `deploy/README.md`.
- Keep project-level decisions in `docs/`; keep backend-specific implementation guidance in `backend/docs/`.

## Commands and ownership

Run frontend commands from `frontend/`:

```bash
npm ci
npm test
npm run lint
npm run build
```

Run backend commands from `backend/`:

```bash
./mvnw -B -ntp clean verify
```

Docker Compose and release scripts are owned by `deploy/`. The frontend image builds from `frontend/`; the backend image builds from `backend/`. Preserve these sibling build contexts.

## Database migration safety

- Flyway migrations live in `backend/src/main/resources/db/migration/`.
- Never edit a migration that may already have been applied in any environment. Add a new versioned migration for schema changes.
- Do not reintroduce manual SQL import as the first-install schema path. Fresh schemas are created by Flyway.
- Do not delete or reset production database data or Docker volumes during tests.

## Monorepo release invariants

- Frontend and backend are verified from the same monorepo commit SHA.
- Keep `.github/workflows/`, `deploy/compose.yaml`, `deploy/release.sh`, and `install.sh` aligned.
- The production deployment must continue to use the exact verified commit and the existing main-branch/production-runner protections.
- A directory move must update Docker build contexts, CI working directories, Playwright paths, deployment scripts, documentation, and regression tests together.

## Verification

For frontend changes, run the applicable checks from `frontend/`. For backend changes, run the applicable Maven tests from `backend/`. For deployment/path changes, also run:

```bash
node --test deploy/test-workflow-pinning.mjs
bash deploy/test-installer.sh
bash deploy/test-release.sh
bash -n deploy/test-nginx-forwarded-ip.sh
```

Do not claim a check passed unless it was actually run. Do not commit secrets, local environment files, build outputs, or generated test artifacts.