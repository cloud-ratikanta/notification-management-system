# Ambiguous-requirement runbook

Goal: Demonstrate how the system resolves ambiguous requirements (e.g., "handle retries safely" or "prevent avoidable duplicates") by making explicit SLAs and implementing policy-driven behavior.

Commands / experiments

1. Validate retry/backoff policy behavior (quick):

```bash
# Run the specific integration test that simulates transient failures then success
./gradlew test --no-daemon --tests "*RetryThenSuccessIntegrationTest"
```

2. Adjust retry policy for testing (example properties in `application.yml` or `application-acceptance.yml`):

# edit Notification properties: maxAttempts, baseDelayMs

3. Run a controlled failing strategy in tests (unit test or local stub) to observe `RETRY_SCHEDULED` and `RETRY_EXHAUSTED` events.

Validation (what to check)

- Audit events should contain `DELIVERY_ATTEMPTED`, `RETRY_SCHEDULED` (with next attempt timestamp), and ultimately `DELIVERY_SUCCEEDED` or `RETRY_EXHAUSTED`.
- Delivery row `attempt_count` and `next_attempt_at` should reflect the backoff policy.
- Idempotency tests: ensure duplicate suppression works as intended by re-submitting same payload and checking audit for `DUPLICATE_SUPPRESSED`.

Representative SQL assertions

```sql
SELECT event_type, payload_json FROM audit_event WHERE notification_id = '<notificationId>' ORDER BY created_at;
SELECT attempt_count, next_attempt_at FROM delivery WHERE notification_id = '<notificationId>'
```

Files/tests that prove the scenario

- `RetryThenSuccessIntegrationTest` (transient then success)
- `RetryExhaustedIntegrationTest` (exhaust retries)
- `IdempotencyExpiryPostgresIT` (idempotency expiry behavior; requires Docker/Testcontainers)

Notes

- Ambiguous requirements should be converted into clear testable properties (e.g., "max retry attempts = 5, base backoff = 1s, jitter = 0.5s").
- Use integration tests to document and lock-in the expected behavior. When possible, capture these expectations in automated tests so future changes cannot silently alter the SLA.

