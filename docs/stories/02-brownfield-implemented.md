# Brownfield — Implemented Changes (summary)

Status: BF-01..BF-05 implemented (strategy extraction, Slack feature flag, idempotency gateway, content dedup, delivery claim safety)

This document summarizes the concrete changes implemented in the repository to satisfy the Brownfield story (docs/stories/02-brownfield.md). It lists implemented features, files added/modified, test coverage, how to run the project and tests, design decisions made, and remaining gaps.

---

## Quick status checklist (BF items)

- BF-01 Refactor channel logic behind Strategy: DONE
- BF-02 Add Slack channel adapter: DONE (strategy + feature flag + unit tests)
- BF-03 HTTP idempotency gateway: DONE (reservation/replay/conflict)
- BF-04 Content deduplication: DONE (dedup table, service, configurable canonicalization, audit)
- BF-05 Delivery claim safety: DONE (claim semantics + attempt uniqueness + tests)

All items have working code and integration/unit tests for the critical paths. The implementation was intentionally additive and preserves existing Greenfield public APIs.

---

## Files added or modified (high-level)

Key new/updated source files (non-exhaustive):

- src/main/java/com/interview/assessment/notification/
  - strategy/ChannelDeliveryStrategy.java, ChannelStrategyRegistry.java (registry and strategy extraction)
  - strategy/SlackDeliveryStrategy.java (Slack adapter; feature-flagged)
  - domain/DefaultChannelRouter.java (router now filters channels without strategies)
  - service/ContentDedupService.java (content canonicalization, configurable)
  - persistence/ContentDedupRepository.java (persistence for dedup records)
  - config/NotificationProperties.java (new `dedup` properties and worker properties)
  - service/NotificationIngestionService.java (integration point: idempotency → content dedup → persist/queue)
  - service/DeliveryOrchestrator.java (unchanged API; delegates to strategies)

Files added/updated under tests:

- src/test/java/com/interview/assessment/notification/
  - NotificationApiIntegrationTest.java (added dedup integration test: two POSTs different keys, same content -> second suppressed)
  - DeliveryStrategyTest.java (added Slack strategy tests)
  - DeliveryRepositoryIntegrationTest.java (integration tests for claim semantics and attempt uniqueness)

Documentation and scripts:

- src/main/resources/schema.sql (added `content_dedup` table and indices)
- src/main/resources/application.yml (added `notification.dedup` defaults and Slack feature flag)
- docs/stories/02-brownfield-implemented.md (this file)

---

## Tests added and behavior

- Integration tests:
  - `NotificationApiIntegrationTest` — new test `differentIdempotencyKeysSameContentSecondSuppressed` verifies that a second POST with a different Idempotency-Key but identical content is suppressed (replays canonical notification) and does not create a new notification/delivery row.
  - `DeliveryRepositoryIntegrationTest` — validates that `delivery_attempt` uniqueness is enforced and that `claimQueuedBatch` only claims PENDING/RETRY rows.

- Unit tests:
  - `DeliveryStrategyTest` — added Slack strategy tests (missing target → `INVALID_RECIPIENT`, present target → success).
  - Router and registry unit tests continue to exercise selection logic (DefaultChannelRouterTest).

All tests were run locally during development:

```bash
./gradlew test
```

BUILD RESULT: tests pass locally in the workspace.

---

## How to run the service locally (quick)

- Run tests:
```bash
./gradlew test
```

- Start the service (default profile uses `application.yml` which enables the in-process worker):
```bash
./gradlew bootRun
```

- Example POST (minimal):
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: k-1" \
  -d '{
	"sourceSystem":"billing-service",
	"eventId":"evt-1",
	"type":"PAYMENT_FAILED",
	"severity":"HIGH",
	"priority":"NORMAL",
	"recipients":[{"recipientId":"user-1","email":"user1@example.com"}],
	"requestedChannels":["EMAIL"]
  }' | jq
```

---

## Important design/implementation decisions

- Strategy extraction: provider-specific I/O (Email, SMS, Slack) lives behind `ChannelDeliveryStrategy`. `DeliveryOrchestrator` and worker work against `ChannelStrategyRegistry` and do not branch on channel type.
- Slack registration: `SlackDeliveryStrategy` is a Spring component conditional on `notification.channels.slack.enabled` to allow safe opt-in. The router filters out channels for which no strategy is registered.
- Idempotency: `IdempotencyService` reserves or replays based on `Idempotency-Key + sourceSystem` and request hash. Replay and conflict semantics are covered by tests.
- Content deduplication: implemented via `content_dedup` table and `ContentDedupService`. The canonicalization is deterministic and configurable via `NotificationProperties.dedup` (windowSeconds, includeEventId, includeTitle, includeBody, includeTemplateParams). Default window = 24h.
- Suppression behavior: dedup check runs after idempotency reservation (so idempotency wins when the same key is replayed) and before inserting the notification. When a dedup hit occurs we append an audit event `DUPLICATE_SUPPRESSED` on the canonical notification and return the canonical accept view with `duplicate=true`.
- Delivery claim safety: claim uses select-then-conditional-update where update predicate ensures transition only from PENDING or RETRY_SCHEDULED to IN_FLIGHT. `delivery_attempt` table has a unique constraint on `(delivery_id, attempt_no)`.

---

