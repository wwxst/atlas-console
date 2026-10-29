# User Account Snowflake ID Design

## Goal

Generate the primary key for ordinary users in `user_account` inside the application with a Snowflake-style 64-bit ID. Keep `user_no` as an independent, human-facing random number and leave administrator, storage, and AI gateway table IDs unchanged.

## Scope

The change applies only to ordinary user creation through the registration service and the `user_account` persistence path. Existing IDs remain valid. The database schema will stop auto-generating new `user_account.id` values, so every new insert must carry an application-generated ID.

## Design

Implement a small local `SnowflakeIdGenerator` without adding a third-party dependency. Its signed-positive `long` layout is:

| Bits | Field | Purpose |
| ---: | --- | --- |
| 41 | timestamp | Milliseconds since a fixed custom epoch |
| 5 | datacenter ID | Deployment-level node grouping |
| 5 | worker ID | Application instance within a datacenter |
| 12 | sequence | IDs generated during the same millisecond |

The generator is synchronized so timestamp and sequence updates are atomic. It increments the sequence for the same millisecond, waits for the next millisecond when the sequence is exhausted, and rejects a clock rollback rather than producing duplicate or out-of-order IDs. The worker and datacenter IDs are supplied through Spring configuration and default to `0` for local development. Values outside the five-bit range fail fast during bean construction.

The registration service obtains an ID before calling `UserAccountMapper.insert`. The mapper writes `id` explicitly and does not use JDBC generated-key handling. `user_no` remains independently generated and existing duplicate-key retry behavior remains limited to the `user_no` unique-index conflict.

## Configuration

Add `app.id-generator.datacenter-id` and `app.id-generator.worker-id` to `application.yml`. Environment variables can override both values. The defaults support a single local instance; deployments with multiple instances must assign unique `(datacenter-id, worker-id)` pairs.

## Error handling

Configuration errors fail application startup. A clock rollback detected by the generator throws an `IllegalStateException`, preventing an unsafe ID from being inserted. Sequence exhaustion waits rather than reusing a sequence value. A database conflict on `user_no` continues to regenerate `user_no` and retry up to the existing limit; conflicts on other indexes retain their current meaning.

## Testing

Unit tests will verify uniqueness under concurrent generation, monotonic ordering for sequential calls, correct worker/datacenter separation, sequence rollover, and clock rollback handling through a controllable clock. Registration tests will verify that the service supplies a non-null ID before insert and uses that ID when creating the session. Mapper and schema checks will verify explicit ID insertion and removal of `AUTO_INCREMENT` from `user_account.id`.

## Alternatives considered

Using a third-party ID library would reduce local implementation code but add dependency and version-management cost for a small, stable primitive. Database auto-increment would remain simple but does not satisfy the requirement for application-generated distributed IDs. A UUID would avoid coordination but would change the existing `BIGINT` contract and index shape. The local Snowflake implementation preserves the current database type and keeps deployment identity explicit.
