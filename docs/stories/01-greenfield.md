# Epic GF — Greenfield: Core notification capability
> **Scenario:** 3.1 Greenfield  
> **HLD:** §2.1, §6, §7, §13  
> **LLD:** §1–§7, §11 (Email/SMS), §12–§14  
Deliver a runnable vertical slice: **REST submit → route → queue → async delivery stubs → status**.
---
## NF-01 — Project scaffold & persistence bootstrap
**As a** developer  
**I want** a Spring Boot 3 / Java 21 app with JDBC, schema, and package layout  
**So that** all greenfield stories share one runnable baseline  
### Acceptance criteria
- [ ] Gradle Spring Boot app boots locally  
- [ ] Packages exist: `controller`, `exception`, `dto`, `service`, `domain.enums`, `domain`, `strategy`, `persistence`, `worker`, `config` under `com.interview.assessment.notification`  
- [ ] `schema.sql` (or Flyway/Liquibase-lite) creates tables per LLD §5 (`notification`, `notification_recipient`, `delivery`, `delivery_attempt`, `audit_event` minimum; idempotency/dedup tables may wait for BF)  
- [ ] H2 `MODE=PostgreSQL` for tests; PostgreSQL-ready SQL  
- [ ] Health or simple ping endpoint optional  
### Technical notes
- `NamedParameterJdbcTemplate`; no JPA required  
- `NotificationProperties` stub in config  
### Depends on
- None  
---
## GF-01 — Domain enums & state vocabulary
**As a** developer  
**I want** shared enums for notification/delivery/channel/severity/failure  
**So that** API, persistence, and worker use one vocabulary  
### Acceptance criteria
- [ ] Enums under `domain.enums`: at least `NotificationStatus`, `DeliveryStatus`, `Channel` (`EMAIL`, `SMS`, `SLACK`), `Severity`, `Priority`, `FailureClass`  
- [ ] Values align with LLD §4 / HLD §7 (including terminals: `REJECTED`, `DUPLICATE`, `EXPIRED`, `FAILED_TERMINAL`, `RETRY_SCHEDULED`, …)  
- [ ] Unit test: enum names serialize stably (e.g. name())  
### Depends on
- NF-01  
---
## GF-02 — Global exception handler & error DTO
**As a** API client  
**I want** consistent JSON errors  
**So that** validation and not-found cases are machine-readable  
### Acceptance criteria
- [ ] `exception.GlobalExceptionHandler` maps validation → `400`, missing entity → `404`, unexpected → `500`  
- [ ] `dto.ErrorResponse` with `code`, `message`, `correlationId`, optional `details[]`  
- [ ] Integration or MockMvc test for invalid POST body → 400  
### Depends on
- NF-01  
---
## GF-03 — Submit notification API (accept path)
**As a** source system  
**I want** `POST /api/v1/notifications` with JSON body  
**So that** I can register an alert for multi-channel delivery  
### Acceptance criteria
- [ ] `controller.NotificationController` + `dto.SubmitNotificationRequest` / `RecipientDto` / `NotificationAcceptResponse`  
- [ ] Required fields validated: `sourceSystem`, `eventId`, `type`, `severity`, `priority`, `recipients` (≥1)  
- [ ] Optional: `correlationId`, `templateKey`/`templateParams`, `scheduleAt`, `expiresAt`, `requestedChannels`, `notificationId`  
- [ ] Server generates `notificationId` and `createdAt` (UTC) when omitted  
- [ ] Happy path returns **`202 Accepted`** with `notificationId`, `status`, `selectedChannels`, `createdAt`  
- [ ] Reject when `expiresAt` ≤ now → `400`  
- [ ] `service.NotificationIngestionService.accept` orchestrates persist + route + queue (may stub route initially if GF-05 not done—prefer same PR as GF-05)  
### Technical notes
- LLD §2.3–§2.4, §3.2  
- Header `Idempotency-Key` may be ignored until BF-03 (document as known gap or no-op)  
### Depends on
- GF-01, GF-02, NF-01  
---
## GF-04 — Persist notification & recipients
**As a** system  
**I want** durable rows for notification and recipients  
**So that** status and delivery can resume after restart  
### Acceptance criteria
- [ ] `persistence.NotificationRepository` inserts `notification` + `notification_recipient` in one transaction with accept  
- [ ] Status starts `ACCEPTED` then moves through `ROUTED`/`QUEUED` per LLD (or combined write ending in `QUEUED` if transitions collapsed—document choice)  
- [ ] Integration test: after POST, rows exist in DB  
### Depends on
- GF-03  
---
## GF-05 — Channel routing policy
**As a** notification service  
**I want** to select channels per recipient using policy  
**So that** delivery targets are explicit and auditable  
### Acceptance criteria
- [ ] `domain.ChannelRouter` + `RoutingPolicy` implement LLD §7 algorithm (prototype-sized):  
  - Start from `requestedChannels` if present, else policy default for severity  
  - Intersect enabled channels + address-capable channels  
  - Remove `blockedChannels`  
  - Soft-order by `preferredChannels`  
  - `CRITICAL` ensures intersection with fast channels (SMS/SLACK) when possible  
  - Empty set → reject `NO_ELIGIBLE_CHANNEL`  
- [ ] Selected channels frozen on notification / drive delivery row creation  
- [ ] Unit tests: default path; blocked SMS; CRITICAL with only email; no eligible → reject  
### Depends on
- GF-01, GF-03  
---
## GF-06 — Create delivery work items (queue)
**As a** system  
**I want** one `delivery` row per recipient×channel  
**So that** the worker can claim and send asynchronously  
### Acceptance criteria
- [ ] On accept, create `delivery` rows with status `PENDING`, `next_attempt_at` ≤ now (or `scheduleAt`)  
- [ ] Notification status becomes `QUEUED` (or equivalent) when deliveries exist  
- [ ] No provider call on the HTTP request thread  
- [ ] Integration test: N recipients × M channels → N×M delivery rows  
### Depends on
- GF-04, GF-05  
---
## GF-07 — Notification status API
**As a** source system or operator  
**I want** `GET /api/v1/notifications/{id}`  
**So that** I can see overall and per-channel delivery state  
### Acceptance criteria
- [ ] Returns `200` with overall `status`, `partialSuccess`, `selectedChannels`, timestamps, `deliveries[]` per LLD §2.5  
- [ ] Unknown id → `404`  
- [ ] Overall status uses roll-up rules LLD §4.3 (may be simplified until worker exists; still correct for `QUEUED`)  
- [ ] `service.StatusQueryService` + repository reads  
### Depends on
- GF-04, GF-06  
---
## GF-08 — Async DeliveryWorker (claim + process)
**As a** system  
**I want** a scheduled worker to claim due deliveries  
**So that** HTTP accept stays fast and work survives process boundaries  
### Acceptance criteria
- [ ] `worker.DeliveryWorker` `@Scheduled` poll per LLD §6  
- [ ] Claim via conditional update (status `PENDING`/`RETRY_SCHEDULED`, `next_attempt_at` ≤ now)  
- [ ] Mark `IN_FLIGHT`, invoke strategy, write `delivery_attempt`  
- [ ] Respect `scheduleAt` / `expiresAt` (expired → no provider call)  
- [ ] Integration test: queued delivery becomes `SUCCEEDED` with stub strategy  
### Depends on
- GF-06, GF-09 (strategies can be minimal stubs)  
---
## GF-09 — Email & SMS strategy stubs
**As a** developer  
**I want** `EmailDeliveryStrategy` and `SmsDeliveryStrategy`  
**So that** the delivery pipeline is real even without vendor accounts  
### Acceptance criteria
- [ ] `strategy.ChannelDeliveryStrategy` + `ChannelStrategyRegistry`  
- [ ] Email/SMS implementations validate address; return success stub (log at info, no secrets)  
- [ ] Map missing address → `INVALID_RECIPIENT` / fail terminal  
- [ ] Unit tests for success and missing address  
### Depends on
- GF-01  
---
## GF-10 — Audit trail (core events)
**As an** operator  
**I want** significant lifecycle events recorded  
**So that** I can explain accept/route/queue/attempt outcomes  
### Acceptance criteria
- [ ] `service.AuditService` appends scrubbed events: at least `NOTIFICATION_ACCEPTED`, `ROUTING_DECISION`, `DELIVERY_QUEUED`, `DELIVERY_ATTEMPTED`, `DELIVERY_SUCCEEDED` / `DELIVERY_FAILED`  
- [ ] Optional `GET /api/v1/notifications/{id}/audit`  
- [ ] Payload JSON has no credentials, no raw full body dumps (template key / ids only)  
- [ ] Test: accept produces expected event types  
### Depends on
- GF-03, GF-08  
---
## GF epic exit criteria
- [ ] End-to-end: POST → 202 → worker delivers Email/SMS stubs → GET status shows success  
- [ ] Demo script or README curl examples  
