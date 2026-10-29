# Snowflake ID Integration Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for the independent frontend and backend tasks. The user authorized repair, merge, commit, and push on 2026-10-16.

**Goal:** Integrate the existing Snowflake feature and local fixes into both main branches without losing user IDs in JSON or browser requests.

**Architecture:** Keep Java/database IDs as Long/BIGINT. Serialize ordinary user response IDs as decimal strings and use string IDs throughout ordinary user frontend calls. Preserve the existing administrator/storage contracts and all existing local changes.

**Tech Stack:** Java 25, Spring Boot/Jackson, MyBatis/MySQL, Redis, React/TypeScript, Node test runner.

## Task 1: Preserve and integrate

- [x] Fetch both origins and preserve existing tracked/untracked work in named Git stashes.
- [x] Fast-forward backend main to origin/feature/user-snowflake-id, then apply the preserved backend work without dropping the backup.
- [x] Confirm the four overlapping modified files merge without conflict and retain both SQL/schema and mapper changes.

## Task 2: Backend JSON boundary

Files: `src/main/java/com/web/project/user/vo/UserAccountListVO.java`, `src/main/java/com/web/project/user/vo/UserInfoVO.java`, serialization regression tests, `docs/api.md`, `scripts/verify_backend.py`.

- [x] Add a regression test using ID `9007199254740993L`; assert real Jackson serialization emits JSON string `"9007199254740993"` for ordinary user list/profile responses, including null IDs.
- [x] Run the regression and confirm it fails because the current response is numeric.
- [x] Annotate the two response ID fields with `@JsonSerialize(using = ToStringSerializer.class)`; retain Long types internally.
- [x] Re-run the regression and update the API contract and real HTTP smoke assertions to check strings and exact ID round trips.

## Task 3: Frontend ordinary user IDs

Files: `src/features/users/api.ts`, `src/pages/UsersPage.tsx`, affected service tests and API documentation.

- [x] Update ordinary user ID types to string and remove any numeric conversion along list, edit, and status request paths.
- [x] Verify actual service calls preserve `9007199254740993` in request URLs and payload handling; verify both ordinary user update and status changes.
- [x] Run `npm test`, `npm run lint`, and `npm run build`; inspect all preserved local frontend changes before committing.

## Review findings and additional regressions

- [x] Preserve Snowflake sequence state when clock rollback interrupts sequence-exhaustion waiting; reproduce duplicate-ID recovery failure before the fix.
- [x] Give the existing avatar integration fixture an explicit ordinary user ID after removing AUTO_INCREMENT.
- [x] Remove exception messages from avatar cleanup warning cause chains while retaining cause types and stack frames; reproduce the logging leak before the fix.
- [x] Reset pagination when global search changes or clears the keyword while retaining other filters.
- [x] Unmount the previous protected page on refresh-token session changes, then rebuild its observers after query cleanup; preserve the page for access-token renewal.
- [x] Independently review the final frontend fixes and verify long-ID edit/disable/enable, pagination reset, cross-tab identity replacement, old-detail removal, and logout synchronization in a browser against disposable MySQL/Redis data.

## Task 4: Final quality and delivery

- [x] Complete backend specification check 1, Review 1, check 2, Review 2, check 3, Review 3, check 4, resolving findings in sequence.
- [x] Run the existing complete backend verification script with JDK 25, disposable MySQL database/private Redis, final Maven verify, HTTP smoke/concurrency checks, and JaCoCo report.
- [x] Inspect final staged diffs for accidental files, credentials, whitespace errors, and regressions.

Delivery commands after the final staged inspection: commit each repository on main and push main to its existing origin without force. Verify each pushed main SHA matches the remote and both workspaces are clean; retain the original safety stashes. Record the actual delivery result in the final verification summary.
