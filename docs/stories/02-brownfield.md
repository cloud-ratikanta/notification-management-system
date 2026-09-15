# Epic BF — Brownfield: Strategy, Slack, idempotency
> **Scenario:** 3.2 Brownfield  
> **HLD:** §2.2, §16  
> **LLD:** §3 (strategy), §8–§9, §11  
Treat greenfield as **existing**. Changes must not break public API field names; additive only.
---
## BF-01 — Refactor channel logic behind Strategy (behavior-preserving)
**As a** maintainer  
**I want** all provider I/O behind `ChannelDeliveryStrategy`  
**So that** new channels do not fork the orchestrator  
### Acceptance criteria
- [ ] `DeliveryOrchestrator` depends only on `ChannelStrategyRegistry`  
- [ ] Email/SMS code lives solely in strategy classes (no channel `switch` in orchestrator)  
- [ ] Existing GF tests still pass (behavior-preserving)  
- [ ] Registry fails closed on unknown channel  
### Depends on
- GF-08, GF-09  
---
## BF-02 — Add Slack channel adapter
**As a** source system  
**I want** Slack as a delivery channel  
**So that** critical alerts can reach chat without new pipeline code  
### Acceptance criteria
- [ ] `strategy.SlackDeliveryStrategy` (webhook stub or configurable URL; secrets from env only)  
- [ ] `Channel.SLACK` enabled via `notification.channels.slack.enabled`  
- [ ] Router may select Slack (address = `slack_target` / `slackUserOrChannel`)  
- [ ] Status shows SLACK delivery rows  
- [ ] Unit test: success stub + missing slack target → `INVALID_RECIPIENT`  
- [ ] No changes required to `NotificationController` contract beyond existing optional fields  
### Depends on
- BF-01, GF-05  
---
## BF-03 — HTTP idempotency gateway
**As a** source system  
**I want** safe retries of `POST` with `Idempotency-Key`  
**So that** network retries do not create duplicate logical notifications  
### Acceptance criteria
- [ ] Header `Idempotency-Key` + `sourceSystem` unique in `idempotency_record`  
- [ ] Same key + same request hash → **`200`** with original `notificationId` (no second notification)  
- [ ] Same key + **different** body hash → **`409`**  
- [ ] `service.IdempotencyService` transactional insert-or-return  
- [ ] Concurrent double-POST covered by unique constraint + re-read  
- [ ] Integration tests for replay and conflict  
### Technical notes
- LLD §8–§9; HLD §8  
- TTL enforcement can be soft until AR-02  
### Depends on
- GF-03, GF-04  
---
## BF-04 — Content deduplication
**As a** platform  
**I want** to suppress avoidable duplicate alerts from chatty producers  
**So that** users are not spammed for the same event  
### Acceptance criteria
- [ ] Content hash over stable fields (e.g. `sourceSystem|eventId|type|severity|normalized recipients|channels`) per LLD §5.1  
- [ ] Hit within window → status `DUPLICATE` (or pointer via `canonical_id`) + audit `DUPLICATE_SUPPRESSED`  
- [ ] Does not break idempotent replay semantics (idempotency key checked first)  
- [ ] Integration test: two POSTs different keys, same content → second suppressed  
### Depends on
- BF-03 (ordering: idempotency then content hash)  
---
## BF-05 — Delivery claim safety (no double-send)
**As a** system  
**I want** concurrent workers not to double-deliver  
**So that** reprocessing queued work has controlled side effects  
### Acceptance criteria
- [ ] Claim SQL/update only transitions from claimable states (LLD §6.2)  
- [ ] Attempt rows append-only; attempt_no unique per delivery  
- [ ] Test or documented single-threaded H2 limitation + PostgreSQL `SKIP LOCKED` intent  
### Depends on
- GF-08  
---
## BF epic exit criteria
- [ ] Slack selectable and visible in status  
- [ ] Idempotent POST demo (curl twice → one notification)  
- [ ] Orchestrator has no channel-specific branches  
