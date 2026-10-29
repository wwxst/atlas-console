# User Account Snowflake ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make application-generated Snowflake IDs the primary keys for newly registered rows in `user_account` while preserving the existing `BIGINT` contract and independent `user_no` behavior.

**Architecture:** Add a small synchronized generator in `com.web.project.common.id`, configured through Spring properties with five-bit worker and datacenter IDs. Inject it into `UserAuthServiceImpl`, assign the account ID before insertion, and change the MyBatis insert and schema to require the explicit ID. Use an injectable millisecond supplier in unit tests so rollover and clock rollback behavior are deterministic.

**Tech Stack:** Java 25, Spring Boot configuration properties, MyBatis XML, JUnit 5, Mockito, MySQL schema SQL.

---

### Task 1: Add generator tests and configuration contract

**Files:**
- Create: `src/test/java/com/web/project/common/id/SnowflakeIdGeneratorTest.java`
- Create: `src/test/java/com/web/project/common/id/SnowflakeIdPropertiesTest.java`

- [ ] **Step 1: Write tests for generator behavior**

  Cover sequential monotonic IDs, concurrent uniqueness, worker/datacenter bits, sequence rollover to the next millisecond, clock rollback rejection, and invalid worker/datacenter values.

- [ ] **Step 2: Run the focused tests to verify they fail for the missing implementation**

  Run: `./mvnw.cmd -Dtest=SnowflakeIdGeneratorTest,SnowflakeIdPropertiesTest test`

  Expected: compilation or test failure because the generator and properties types do not exist yet.

- [ ] **Step 3: Commit the failing tests**

  Run: `git add src/test/java/com/web/project/common/id && git commit -m "test: define snowflake id behavior"`

### Task 2: Implement the Snowflake generator and Spring configuration

**Files:**
- Create: `src/main/java/com/web/project/common/id/SnowflakeIdGenerator.java`
- Create: `src/main/java/com/web/project/config/properties/SnowflakeIdProperties.java`
- Create: `src/main/java/com/web/project/config/SnowflakeIdConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Implement the minimal generator for the tests**

  Use a 41/5/5/12 positive-long layout, a fixed epoch, synchronized state updates, sequence rollover waiting, and rollback rejection. Keep the timestamp supplier constructor available to tests while the normal constructor uses `System.currentTimeMillis`.

- [ ] **Step 2: Bind and validate configuration**

  Add `app.id-generator.datacenter-id` and `app.id-generator.worker-id`, both defaulting to `0`, and fail fast when either value is outside `0..31`. Register one generator bean from the properties.

- [ ] **Step 3: Run focused tests and the existing unit suite**

  Run: `./mvnw.cmd -Dtest=SnowflakeIdGeneratorTest,SnowflakeIdPropertiesTest test`

  Expected: PASS.

  Run: `./mvnw.cmd test`

  Expected: PASS before changing registration wiring.

- [ ] **Step 4: Commit the generator implementation**

  Run: `git add src/main/java src/main/resources/application.yml && git commit -m "feat: add configurable snowflake id generator"`

### Task 3: Make registration assign IDs before insert

**Files:**
- Modify: `src/main/java/com/web/project/auth/service/impl/UserAuthServiceImpl.java`
- Modify: `src/test/java/com/web/project/auth/service/impl/UserAuthServiceImplTest.java`

- [ ] **Step 1: Update registration tests to require a service-generated ID**

  Inject a mocked `SnowflakeIdGenerator` into the service fixture, stub `nextId`, and assert the inserted `UserAccount` carries that ID and the session is created with the same ID. Remove helpers that simulate a database-generated key.

- [ ] **Step 2: Run the focused registration tests to verify the new expectation fails**

  Run: `./mvnw.cmd -Dtest=UserAuthServiceImplTest test`

  Expected: failure because registration does not yet call the generator.

- [ ] **Step 3: Inject and call the generator**

  Add the generator constructor dependency and set `account.setId(snowflakeIdGenerator.nextId())` before `insertAccount(account)`. Preserve all existing `user_no` retry and duplicate-index behavior.

- [ ] **Step 4: Run the focused registration tests**

  Run: `./mvnw.cmd -Dtest=UserAuthServiceImplTest test`

  Expected: PASS.

- [ ] **Step 5: Commit the service change**

  Run: `git add src/main/java/com/web/project/auth/service/impl/UserAuthServiceImpl.java src/test/java/com/web/project/auth/service/impl/UserAuthServiceImplTest.java && git commit -m "feat: assign user snowflake id during registration"`

### Task 4: Update mapper and database contract

**Files:**
- Modify: `src/main/resources/mapper/user/UserAccountMapper.xml`
- Modify: `src/main/java/com/web/project/user/mapper/UserAccountMapper.java`
- Modify: `web_project.sql`
- Modify: `src/test/java/com/web/project/persistence/MapperIntegrationTest.java`

- [ ] **Step 1: Change the mapper test to set an explicit ID**

  Set a deterministic ID on the test account before `insert`, assert it is retained, and keep the rest of the mapping assertions unchanged.

- [ ] **Step 2: Run the mapper test to observe the schema or mapper contract failure**

  Run: `./mvnw.cmd -Dtest=MapperIntegrationTest test`

  Expected: skipped without `ATLAS_MYSQL_TEST_URL`, or failure against an old schema because explicit IDs are not yet fully supported.

- [ ] **Step 3: Write the explicit-ID mapper insert**

  Remove generated-key attributes and include `id` in the insert column and values lists. Update the JavaDoc to state that the caller supplies the primary key.

- [ ] **Step 4: Remove only `AUTO_INCREMENT` from `user_account.id`**

  Leave all other table auto-increment definitions unchanged. Update integration assertions so they verify the inserted ID remains the supplied value.

- [ ] **Step 5: Run mapper and full tests**

  Run: `./mvnw.cmd -Dtest=MapperIntegrationTest test`

  Run: `./mvnw.cmd test`

  Expected: the mapper test is skipped when the optional MySQL environment variable is absent; all enabled tests pass.

- [ ] **Step 6: Commit persistence changes**

  Run: `git add src/main/java/com/web/project/user/mapper/UserAccountMapper.java src/main/resources/mapper/user/UserAccountMapper.xml web_project.sql src/test/java/com/web/project/persistence/MapperIntegrationTest.java && git commit -m "feat: persist explicit user account ids"`

### Task 5: Final verification and review

**Files:**
- Review all files changed by Tasks 1-4.

- [ ] **Step 1: Run formatting and whitespace checks**

  Run: `git diff --check`

- [ ] **Step 2: Run the complete verification lifecycle**

  Run: `./mvnw.cmd verify`

  Expected: exit code `0`; report any optional MySQL integration test skip explicitly.

- [ ] **Step 3: Audit the final diff against the design**

  Confirm only `user_account` creation changed to application-generated IDs, generated-key handling is gone for that insert, worker/datacenter configuration is documented, and no unrelated table ID behavior changed.

- [ ] **Step 4: Request code review and address findings**

  Review the committed diff against the design and tests. Fix all critical and important findings, rerun verification, and leave minor findings documented if they are outside this scope.
