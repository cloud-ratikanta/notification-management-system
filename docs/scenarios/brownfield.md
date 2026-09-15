# Brownfield runbook

Goal: Show how the system can be extended safely (adding a channel adapter, verifying routing changes, and ensuring no regressions via tests).

Commands

1. Run unit/integration tests locally (H2 acceptance):

```bash
./gradlew test --no-daemon --tests "*RetryThenSuccessIntegrationTest"
```

2. Add or modify a channel strategy (example location):

- Channel implementations: `src/main/java/com/interview/assessment/notification/strategy/`
- Registry: `src/main/java/com/interview/assessment/notification/strategy/ChannelStrategyRegistry.java`

3. Re-run the integration tests and the demo script to validate changes:

```bash
./gradlew bootJar -x test
./scripts/run-local-e2e.sh
```

Validation (what to check)

- Routing audit event (`ROUTING_DECISION`) reflects the new channel when eligible.
- No behavioral regressions: existing tests (RetryThenSuccessIntegrationTest, RetryExhaustedIntegrationTest) still pass.
- Delivery continues to succeed using the registry lookup.

Representative SQL assertions

```sql
SELECT selected_channels FROM notification WHERE id = '<notificationId>' -- includes new channel
SELECT event_type FROM audit_event WHERE notification_id = '<notificationId>' ORDER BY created_at;
```

Files/tests that prove the scenario

- Strategy implementations in `src/main/java/.../strategy/`
- `RetryThenSuccessIntegrationTest` and `RetryExhaustedIntegrationTest`
- `scripts/run-local-e2e.sh` to validate runtime behavior with stubs

Notes

- Brownfield changes should include unit tests for the new channel strategy and integration tests covering routing.
- Prefer making the registry resilient: if a channel strategy is missing, the system should emit a `NO_ELIGIBLE_CHANNEL` or a safe fallback audit event rather than throwing at runtime.

