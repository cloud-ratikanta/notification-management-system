# Greenfield — Implemented Changes (summary)

Status: Completed vertical slice for Greenfield (submit → route → queue → async delivery stubs → status)

This document summarizes the concrete changes implemented in the repository to satisfy the Greenfield story (docs/stories/01-greenfield.md). It lists implemented features, files added/modified, test coverage, how to run the project and tests, design decisions made, and remaining gaps moving into the Brownfield phase.

---

## Quick status checklist (GF items)

- NF-01 Project scaffold & persistence: DONE
- GF-01 Enums & vocabulary: DONE (code present; small unit test suggested)
- GF-02 Global exception handler & error DTO: DONE (handler and ErrorResponse; correlation id echo implemented)
- GF-03 Submit notification API: DONE (controller, DTOs, validation, id generation)
- GF-04 Persist notification & recipients: DONE (transactional insert)
- GF-05 Channel routing policy: DONE (DefaultChannelRouter + RoutingPolicy)
- GF-06 Create delivery work items (queue): DONE (delivery rows per recipient×channel)
- GF-07 Notification status API: DONE (StatusQueryService with roll-up)
- GF-08 Async DeliveryWorker (claim + process): DONE (worker, claim logic, orchestrator)
- GF-09 Email & SMS strategy stubs: DONE (stub strategies validate address, return DeliveryResult)
- GF-10 Audit trail: DONE (AuditService + repository + events appended)

All above items have working code and integration/unit tests for many critical paths. Some unit tests and audit assertions were added; a few more tests are recommended before Brownfield.

---

## Files added or modified (high-level)

Key new/updated source files (non-exhaustive):

- src/main/java/com/interview/assessment/notification/
  - controller/NotificationController.java (API endpoints)
  - service/NotificationIngestionService.java (accept, route, persist, queue)
  - service/StatusQueryService.java (status API and roll-up)
  - service/DeliveryOrchestrator.java (invoke strategies; record attempt)
  - worker/DeliveryWorker.java (scheduled claim + dispatch)
  - strategy/EmailDeliveryStrategy.java, SmsDeliveryStrategy.java, ChannelStrategyRegistry.java
  - persistence/NotificationRepository.java, DeliveryRepository.java, AuditRepository.java, IdempotencyRepository.java
  - exception/GlobalExceptionHandler.java (now extracts and populates correlation id)
  - dto/* (SubmitNotificationRequest, RecipientDto, NotificationAcceptResponse, NotificationStatusResponse, ErrorResponse, DeliveryStatusDto)
  - domain/DefaultChannelRouter.java, RoutingPolicy.java, RoutingInput.java, RoutingOutcome.java

Files added under tests:

- src/test/java/com/interview/assessment/notification/
  - DefaultChannelRouterTest.java (unit tests)
  - DeliveryStrategyTest.java (unit tests for Email/SMS strategies)
  - E2EWorkerIntegrationTest.java (integration test that posts a notification and invokes DeliveryWorker.tick() until success)

Documentation and scripts:

- src/main/resources/schema.sql (DDL — includes schedule_at/expires_at columns)
- README.md updated with quick demo curl examples
- docs/stories/Burno collection requests response sample.md updated with 10 example requests/responses
- docs/stories/01-greenfield-implemented.md (this file)

---

## Tests added and behavior

- Integration tests:
  - NotificationApiIntegrationTest (existing) — validates accept/get/audit/idempotency/conflict scenarios
  - E2EWorkerIntegrationTest — submits a notification, runs DeliveryWorker.tick() in a loop and asserts delivery reaches SUCCEEDED

- Unit tests:
  - DefaultChannelRouterTest — router behavior for defaults, blocked channels, CRITICAL fast channels, and no-address case
  - DeliveryStrategyTest — Email/SMS strategy success and missing-address failure

All tests were run locally:

```bash
./gradlew test
```

BUILD RESULT: tests pass locally (after schema.sql fix and the test robustness improvements).

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

- Schema aligned to repository code: `notification` table contains `schedule_at` and `expires_at` (these were missing initially and were added to `src/main/resources/schema.sql`).
- The service collapses state transitions at accept and persists NotificationStatus.QUEUED (rather than multiple writes for ACCEPTED→ROUTED→QUEUED). Roll-up logic in `StatusQueryService` computes IN_PROGRESS / COMPLETED / FAILED etc from delivery rows.
- Idempotency: an `IdempotencyRepository` and `IdempotencyService` are present. Replay returns a 200 + duplicate view; conflicts return 409. Tests exercise replay & conflict paths.
- Worker: `DeliveryWorker` polls and claims rows with conditional UPDATE, increments attempt_count, marks IN_FLIGHT, and `DeliveryOrchestrator` records attempts and marks success/failure.
- Strategies: Email/SMS are implemented as lightweight stubs that validate recipient address and return success or FailureClass.INVALID_RECIPIENT.

---

## Remaining gaps & recommended quick wins before Brownfield

- Add unit tests for DeliveryOrchestrator (mock DeliveryRepository/AuditService/ChannelStrategyRegistry) to assert attempt recording and audit calls.
- Strengthen audit endpoint assertions (test for presence and ordering of events: NOTIFICATION_ACCEPTED, ROUTING_DECISION, DELIVERY_QUEUED, DELIVERY_ATTEMPTED, DELIVERY_SUCCEEDED/FAILED).
- Add index on `delivery(next_attempt_at)` in schema for worker performance.
- Consider exposing/exposing Correlation-Id from MDC or request header in all logs and errors consistently.
- Add more strategy tests (Slack stub) and tests for retry backoff logic if implemented later.

---

## Suggested next steps (Brownfield start)

1. Introduce Strategy pattern improvements: parameterize providers, add configuration for provider endpoints and retry/backoff.
2. Improve idempotency persistence semantics (hash algorithm, retention policy) and add tests for replay across restarts.
3. Add feature flags and configuration-driven routing policies (move RoutingPolicy to be configurable via properties or DB).
4. Start refactoring data access to enable transactional retries and to support PostgreSQL-specific features if needed.

---

If you want, I can now prepare a small PR summary (files changed) to commit, or generate a Postman/Insomnia collection from the example requests. What would you like me to do next as you move into Brownfield work?

