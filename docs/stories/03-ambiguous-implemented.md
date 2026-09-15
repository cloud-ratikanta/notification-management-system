# 03 — Ambiguous requirements: implemented work

This document lists what was implemented in the codebase to satisfy the "Ambiguous requirements" epic (turn vague asks into testable policy and behavior), where to find the artifacts, and the current status of acceptance criteria.

Summary of implemented items

- Failure taxonomy & retry policy
  - Implemented FailureClassifier and RetryPolicy used by the orchestrator/worker to classify provider errors and compute backoff.
  - Location: `src/main/java/com/interview/assessment/notification/service/` and `src/main/java/com/interview/assessment/notification/domain/` (see `FailureClassifier`, `RetryPolicy`, `DeliveryOrchestrator`).

- Idempotency expiry semantics
  - Idempotency handling supports expiry (records with `expires_at` are treated as misses). Postgres IT demonstrates race conditions and expiry behavior.
  - Location: `src/main/java/.../service/IdempotencyService.java` and `src/test/java/.../IdempotencyExpiryPostgresIT.java` (Testcontainers-based).

- Audit privacy & event set
  - Audit events for routing, queueing, attempts, retries, successes and terminal failures are produced by the flow. An `AuditSanitizer` is used to scrub payloads.
  - Location: `src/main/java/com/interview/assessment/notification/service/AuditService.java` (constructor consolidated and default sanitizer provided).

- Status roll-up & partial success
  - Notification status roll-up implemented in the core service and reflected in API responses (`COMPLETED`, `FAILED`, `IN_PROGRESS`, `partialSuccess` flag).
  - Location: relevant logic in `DeliveryOrchestrator`, `NotificationService` and response DTOs.

- Operability / config keys
  - Properties exposed via `application*.yml` and bound to `NotificationProperties`. Worker, backoff and TTL keys are configurable.
  - Location: `src/main/resources/application-acceptance.yml`, `application.yml`, and `src/main/java/.../config/NotificationProperties.java`.

- Local E2E & demo tooling (helps validate ambiguous behaviors interactively)
  - `LocalE2EConfig` registers stub channel strategies (EMAIL/SMS/SLACK) so retries and success paths can be simulated locally without network calls.
  - `InternalProcessController` exposes `POST /internal/process-now` (active in `local-e2e` profile) to trigger `DeliveryWorker.tick()` manually.
  - Demo script: `scripts/run-local-e2e.sh` (build, start app with `acceptance,local-e2e`, submit sample payload, trigger worker, poll audit, stop app).
  - Sample payload: `docs/samples/payload-e2e.json`.

Files and artifacts added or modified (absolute paths)

- Demo & samples
  - /home/ratikantan/IdeaProjects/notification-management-system/scripts/run-local-e2e.sh
  - /home/ratikantan/IdeaProjects/notification-management-system/docs/samples/payload-e2e.json

- Local E2E support
  - /home/ratikantan/IdeaProjects/notification-management-system/src/main/java/com/interview/assessment/notification/config/LocalE2EConfig.java
  - /home/ratikantan/IdeaProjects/notification-management-system/src/main/java/com/interview/assessment/notification/controller/InternalProcessController.java

- AuditService fix
  - /home/ratikantan/IdeaProjects/notification-management-system/src/main/java/com/interview/assessment/notification/service/AuditService.java
    - Consolidated constructors to a single `@Autowired` constructor to remove DI ambiguity and guarantee a default `AuditSanitizer`.

- Documentation
  - README.md updated with a demo snippet and Testcontainers/Docker note: `/home/ratikantan/IdeaProjects/notification-management-system/README.md`
  - Testcases doc clarified for Docker/Testcontainers: `/home/ratikantan/IdeaProjects/notification-management-system/docs/testcases.md`
  - Scenario runbooks added:
    - `/home/ratikantan/IdeaProjects/notification-management-system/docs/scenarios/greenfield.md`
    - `/home/ratikantan/IdeaProjects/notification-management-system/docs/scenarios/brownfield.md`
    - `/home/ratikantan/IdeaProjects/notification-management-system/docs/scenarios/ambiguous.md`

Tests (implemented / existing)

- H2-based acceptance/integration tests (fast):
  - `RetryThenSuccessIntegrationTest` — demonstrates transient failure then success
  - `RetryExhaustedIntegrationTest` — demonstrates retries exhausted path

- Testcontainers Postgres ITs (DB-specific):
  - `IdempotencyExpiryPostgresIT` — demonstrates idempotency expiry behavior (requires Docker/Testcontainers)

Acceptance criteria status (per AR items in `docs/stories/03-ambiguous.md`)

- AR-01 (Failure taxonomy & bounded retry)
  - Implemented: classifier and retry policy exist and are exercised by tests. Configuration-driven retry parameters available.
  - Pending/Manual: update documentation with exact default numeric policy (e.g., maxAttempts=5, baseDelayMs) if you want them locked in README and `application-*.yml`.

- AR-02 (24-hour idempotency & dedup retention)
  - Implemented: expiry-aware idempotency; Postgres IT covers expiry behavior.
  - Pending: add a scheduled purge job (optional) — currently system treats expired records as misses (lazy expiry).

- AR-03 (Audit privacy & complete event set)
  - Implemented: audit events and sanitizer are present; `AuditService` ensures sanitized payloads.
  - Pending: operational review & checklist to ensure all PII/token patterns are scrubbed (recommend security review for production readiness).

- AR-04 (Status roll-up & partial success)
  - Implemented: roll-up logic exists and is visible in API responses (partialSuccess flag used). Covered by integration tests for typical flow.

- AR-05 (Configuration & operability)
  - Implemented: keys exposed and `NotificationProperties` bound. README updated with key demo instructions.
  - Pending: consolidate a single documentation table listing all configuration keys and their defaults (small doc addition recommended).

How to validate locally (quick checklist)

1. Run the automated demo (recommended):

```bash
chmod +x ./scripts/run-local-e2e.sh
./scripts/run-local-e2e.sh
```

Expected: `DELIVERY_SUCCEEDED` appears in the audit output and final notification status is `COMPLETED` with `deliveries[].status = SUCCEEDED`.

2. Run targeted integration tests (H2):

```bash
./gradlew test --no-daemon --tests "*RetryThenSuccessIntegrationTest"
./gradlew test --no-daemon --tests "*RetryExhaustedIntegrationTest"
```

3. If you want to run Postgres-specific idempotency expiry ITs, ensure Docker is running and execute:

```bash
./gradlew test --no-daemon --tests "*IdempotencyExpiryPostgresIT"
```

Notes and next recommended small tasks

- Add a one-page "policy card" (numbers and formulas) to `docs/stories/03-ambiguous.md` or as a new `docs/policies/retries.md` that lists:
  - Failure classification table, backoff formula, jitter bounds, default max attempts and TTLs.
- Add a small CI workflow that runs Testcontainers tests on a Docker-enabled runner so reviewers can see Postgres ITs pass automatically.
- Optional: add an administrative SQL script to purge old idempotency records if you want explicit cleanup instead of lazy expiry.

If you want, I can (pick one):
- add the policy card file with exact numeric defaults and formula, or
- scaffold a GitHub Actions workflow that runs the Testcontainers-based tests on ubuntu-latest (with Docker), or
- create the optional scheduled purge job and related migration scripts.

---
Generated on: 2026-09-15

