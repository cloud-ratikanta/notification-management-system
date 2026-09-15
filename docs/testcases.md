# Test Cases for Notification Management System

This document lists test scenarios (happy path, edge cases, negative tests, and integration tests) to validate the core features of the Notification Management System.

Location: `docs/testcases.md`

## Table of contents
- Test environment & how to run
- API / ingestion tests
- Idempotency tests
- Content deduplication tests
- Routing tests
- Delivery / orchestrator tests
- Retry behaviour tests
- Worker / background processing tests
- Channel strategy tests
- Audit & persistence tests
- Integration / Acceptance tests (H2)
- Postgres-specific tests (Testcontainers)
- Non-functional tests
- Test data and utilities

---

## Test environment & how to run

- Default local acceptance profile uses in-memory H2 DB and disables the scheduled worker. Use profile `acceptance` for deterministic HTTP-only tests.
- For local end-to-end demos (worker + stubbed providers) use profiles: `acceptance,local-e2e` and enable the worker: `-Dnotification.worker.enabled=true`.
- For Postgres-backed integration tests, ensure Docker is available and run Testcontainers tests.

Run a single test class using Gradle:

```
./gradlew test --no-daemon --no-parallel --tests "*RetryThenSuccessIntegrationTest"
```

Run all tests (slow):

```
./gradlew test
```

Build and run app for manual E2E:

```
./gradlew bootJar -x test
java -Dspring.profiles.active=acceptance,local-e2e -Dnotification.worker.enabled=true -jar build/libs/notification-management-system-0.0.1-SNAPSHOT.jar
```

---

## API / Ingestion tests

1. Happy path — submit notification (HTTP)
   - Action: POST `/api/v1/notifications` with a valid `SubmitNotificationRequest` including one recipient with an email.
   - Expected: HTTP 202 (Accepted) with JSON body containing `notificationId`, `status: QUEUED`, `duplicate:false`.
   - DB checks (H2):
     - `SELECT * FROM notification WHERE id = '<notificationId>'` → status QUEUED, selected_channels contains EMAIL.
     - `SELECT * FROM delivery WHERE notification_id = '<notificationId>'` → one PENDING delivery row.
     - `SELECT * FROM audit_event WHERE notification_id = '<notificationId>'` → events include `ROUTING_DECISION`, `DELIVERY_QUEUED`, `NOTIFICATION_ACCEPTED`.
   - Sample curl (save response to file):

```
curl -sS -X POST http://localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' -d @payload.json | tee /tmp/post_resp.json
```

2. Missing recipient contact
   - Action: POST with recipient that has no email/phone/slack target.
   - Expected: 400 Bad Request with clear error `NO_ELIGIBLE_CHANNEL` or `INVALID_RECIPIENT` depending on validation.

3. Invalid schedule/window
   - Action: POST with `expiresAt` in the past or `expiresAt` ≤ `scheduleAt`.
   - Expected: 400 Bad Request with error messages.

---

## Idempotency tests

1. Idempotent replay (same idempotency key, same request hash)
   - Action: POST the same payload twice with same `Idempotency-Key` header.
   - Expected: first request accepted (202), second returns existing accepted view (200 OK with duplicate true) or replay view (service returns same notificationId and duplicate=true).
   - DB checks: `idempotency_record` exists with `idempotency_key` and `request_hash` matching.

2. Idempotency collision with different hash
   - Action: same idempotency key but modified payload -> different request hash.
   - Expected: second call treated as conflict OR handled according to `reserveOrReplay` semantics (either reject or record duplicate). Behavior should match implementation (test verifies intended behavior).

3. Expired idempotency allows new notification (Integration)
   - Setup: Insert idempotency record with `expires_at` in the past for `sourceSystem + key`.
   - Action: Submit new notification with same sourceSystem and idempotency key.
   - Expected: System treats it as a new request (new notification created) and not replayed.
   - Test file example: `src/test/java/.../IdempotencyExpiryPostgresIT.java` demonstrates Postgres behavior.

---

## Content deduplication tests

1. Dedup suppressed creation
   - Action: Submit notification A, then submit notification B with identical content within dedup window.
   - Expected: Second submission returns a replay view and audit has `DUPLICATE_SUPPRESSED` referencing canonical id.

2. Dedup outside window
   - Action: Submit two notifications with same content but after dedup window expiry (simulate by adjusting `NotificationProperties.dedup.windowSeconds` or timestamps).
   - Expected: Second notification is created normally.

---

## Routing tests

1. Channel routing decision
   - Action: Submit notification with recipient preferences to influence routing.
   - Expected: `ROUTING_DECISION` audit event contains the selectedChannels; `Notification.selectedChannels` contains expected channels.

2. No eligible channels
   - Action: Submit a payload where routing results in empty channels (e.g., recipient blocked all channels).
   - Expected: HTTP 400 with `NO_ELIGIBLE_CHANNEL`.

---

## Delivery / Orchestrator tests

1. Delivery attempt success (unit/integration)
   - Action: Use a stubbed `ChannelDeliveryStrategy` that returns `DeliveryResult.ok()` and call `DeliveryOrchestrator.process(row)` or let worker process queued delivery.
   - Expected: `DELIVERY_ATTEMPTED` and `DELIVERY_SUCCEEDED` audit events, `delivery.status` => SUCCEEDED, `delivery_attempt` record logged with outcome SUCCEEDED.
   - Test file example: `src/test/java/.../RetryThenSuccessIntegrationTest.java` contains a stub pattern.

2. Delivery transient failure then success (retry)
   - Action: Stub first attempt to fail transiently (e.g., return `failed` with `FailureClass.TRANSIENT_PROVIDER_FAILURE`), second attempt succeeds.
   - Expected: audit contains `RETRY_SCHEDULED` then `DELIVERY_SUCCEEDED`; delivery attempt logging includes FAILED then SUCCEEDED.

3. Delivery permanent failure
   - Action: Strategy returns permanent failure (non-retryable FailureClass), worker should mark terminal failure.
   - Expected: `DELIVERY_FAILED` and `RETRY_EXHAUSTED` (with reason `non_retryable`) audit events; delivery status = FAILED_TERMINAL.

---

## Retry behaviour tests

1. Exhaust retries
   - Action: Configure a short retry policy (maxAttempts small) and use an always-failing transient strategy.
   - Expected: After configured attempts, audit contains `RETRY_SCHEDULED` and eventually `RETRY_EXHAUSTED` with reason `max_attempts` and delivery status = FAILED_TERMINAL.
   - Test file example: `src/test/java/.../RetryExhaustedIntegrationTest.java`.

2. Rate-limit retryAfter handling
   - Action: Strategy returns `failedWithRetry(..., retryAfterSeconds)` and check next attempt uses the provided retryAfter when computing nextAttemptAt.
   - Expected: `scheduleRetry` sets `next_attempt_at` respecting retryAfter value.

---

## Worker / Background processing tests

1. Worker claims and processes deliveries
   - Action: Start app with `notification.worker.enabled=true` and `local-e2e` profile (stub strategies). Submit a notification then wait for worker or call `/internal/process-now`.
   - Expected: Worker picks up PENDING deliveries, increments `attempt_count`, calls orchestrator, and marks SUCCEEDED/FAILED appropriately.

2. Worker expiry handling
   - Action: Submit a notification with `expiresAt` in the past so deliveries should be marked expired when worker picks them up.
   - Expected: `DELIVERY_FAILED` with payload `{failureClass:EXPIRED}` and delivery status = EXPIRED.

---

## Channel strategy tests

1. Email strategy validation
   - Action: Call `EmailDeliveryStrategy.deliver()` with missing email.
   - Expected: returns `DeliveryResult.failed(FailureClass.INVALID_RECIPIENT, ...)`.

2. Local-e2e stubs
   - Action: Start app with `local-e2e` profile and ensure `LocalE2EConfig` registered stub strategies for EMAIL/SMS/SLACK.
   - Expected: deliveries succeed (DELIVERY_SUCCEEDED) without external network calls.

---

## Audit & persistence tests

1. Audit append and retrieval
   - Action: Use `AuditService.append(...)` via flows and verify `AuditRepository.findByNotificationId(notificationId)` returns expected events in order.
   - Expected: events match the occurrence order and sanitized payloads where applicable.

2. Delivery attempt recording
   - Action: Verify `delivery_attempt` rows are inserted for each attempt and columns contain correct attempt_no and outcome.

---

## Integration / Acceptance tests (H2)

- Include H2-based integration tests that exercise high-level flows without Docker.
- Tests to include:
  - `RetryThenSuccessIntegrationTest` (transient failure then success)
  - `RetryExhaustedIntegrationTest` (always-fail until max attempts)
  - DeliveryRepository integration tests (insert/claim/schedule/mark updates)

Run with Gradle:
```
./gradlew test --tests "*RetryThenSuccessIntegrationTest" --no-daemon
```

---

## Postgres-specific tests (Testcontainers)

- Testcontainers-based integration tests validate DB-specific behaviour (SKIP_LOCKED, UNIQUE constraints, idempotency expiry races) — e.g. `IdempotencyExpiryPostgresIT`.
- Prerequisite: Docker available and accessible by Testcontainers.
- Run single test (fast):

```
./gradlew test --no-daemon --tests "*IdempotencyExpiryPostgresIT"
```

If Docker is not available, these tests will fail. A common error message is:

```
Could not find a valid Docker environment. Please check configuration. Attempted configurations were:
    UnixSocketClientProviderStrategy: failed with exception InvalidConfigurationException (Could not find unix domain socket). Root cause NoSuchFileException (/var/run/docker.sock)
java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
```

Recommended remediation:

- Ensure Docker is installed and the daemon is running locally (e.g., `systemctl start docker` on many Linux systems).
- Ensure your user can access the Docker socket (`/var/run/docker.sock`) or run tests as a user that can.
- For CI, pick a runner with Docker support or configure Testcontainers to use a remote Docker endpoint (see Testcontainers docs).
- If you do not have Docker available, rely on the H2-based integration tests for fast feedback and skip Testcontainers tests in CI when necessary.

---

## Non-functional tests

1. Performance: create a load test that submits N notifications and measures throughput and DB connections. Verify worker keeps up and no resource leaks.
2. Resilience: simulate DB transient failures (kill connection) and ensure retries/recovery work as expected.

---

## Test data and utilities

- Sample payload files for manual testing: `docs/samples/payload-e2e.json` (create if desired).
- Helper commands:
  - Manual processing trigger: `POST /internal/process-now` (only active in `local-e2e` profile).
  - Health: `GET /actuator/health`

---

## Mapping of implemented tests in repository

- `src/test/java/com/interview/assessment/notification/integration/RetryThenSuccessIntegrationTest.java` — simulated transient then success path.
- `src/test/java/com/interview/assessment/notification/integration/RetryExhaustedIntegrationTest.java` — retries exhausted path.
- `src/test/java/com/interview/assessment/notification/integration/IdempotencyExpiryPostgresIT.java` — Postgres idempotency expiry IT (requires Docker/Testcontainers).

---

If you want, I can also:
- generate example JSON payload files under `docs/samples/` referenced above, and
- add a small shell script `scripts/run-local-e2e.sh` that starts the app, sends a test notification, triggers processing and prints the audit events for demonstration.

Please tell me if you'd like me to add the sample payloads and the demo script, and whether to place `testcases.md` in a different folder (e.g., `doc/testcases.md`).

