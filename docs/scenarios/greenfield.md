# Greenfield runbook

Goal: Demonstrate the core ingestion -> routing -> delivery flow using the in-memory acceptance profile and local stub strategies for deterministic delivery.

Commands

1. Build & start app with stubs and worker enabled:

```bash
./gradlew bootJar -x test
java -Dspring.profiles.active=acceptance,local-e2e -Dnotification.worker.enabled=true -jar build/libs/notification-management-system-0.0.1-SNAPSHOT.jar
```

2. Submit sample notification (uses example payload in `docs/samples/payload-e2e.json`):

```bash
curl -sS -X POST http://localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' -d @docs/samples/payload-e2e.json | jq .
```

3. Trigger worker (manual, available only in `local-e2e` profile):

```bash
curl -X POST http://localhost:8080/internal/process-now
```

Validation (what to check)

- API response: `202 Accepted` with `notificationId` in JSON.
- Audit events: `NOTIFICATION_ACCEPTED`, `ROUTING_DECISION`, `DELIVERY_QUEUED`, then `DELIVERY_ATTEMPTED` and `DELIVERY_SUCCEEDED`.
- Delivery row: `SELECT * FROM delivery WHERE notification_id = '<id>'` → `status = 'SUCCEEDED'`, `attempt_count >= 1`.

Representative SQL assertions (H2):

```sql
SELECT status FROM notification WHERE id = '<notificationId>' -- expect QUEUED then COMPLETED/SUCCEEDED
SELECT status, attempt_count FROM delivery WHERE notification_id = '<notificationId>' -- expect SUCCEEDED
SELECT event_type FROM audit_event WHERE notification_id = '<notificationId>' ORDER BY created_at;
```

Files/tests that prove the scenario

- `docs/samples/payload-e2e.json` (sample payload)
- `scripts/run-local-e2e.sh` (automated demo)
- Integration tests: `RetryThenSuccessIntegrationTest` (patterns used here)

Notes

- `local-e2e` profile registers stub strategies for EMAIL/SMS/SLACK so no external network calls are made.
- Use the demo script `./scripts/run-local-e2e.sh` for a one-command demo that automates these steps.

