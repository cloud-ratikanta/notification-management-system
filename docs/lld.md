# Low-Level Design (LLD) — Notification Management Service

> **Document type:** Low-Level Design (stakeholder walkthrough order)  
> **Parent HLD:** [`architecture.md`](architecture.md)  
> **Related:** [`approach.md`](approach.md) · [`README.md`](../README.md)  
> **Stack:** Java 21 · Spring Boot 3 · Spring JDBC · PostgreSQL / H2

This document specifies **how** the service is built: APIs, sequences, class design, DDL, state transitions, algorithms, and configuration. System-level boundaries and rationale live in the HLD.

**How to read this doc**

| Part | Contents | Audience use |
|------|----------|--------------|
| **§1–§14** | Implementation design | Primary walkthrough |
| **§15** | Requirement traceability | Review checklist |
| **Appendix A–B** | Orientation & client narrative | Q&A only |

---

## 1. End-to-end sequence (ingress + internal async)

Primary mental model for reviews: source systems call **REST/JSON**; delivery to providers is **asynchronous** after accept. Fuller client narrative: [Appendix B](#appendix-b--client-integration-narrative).
```text
  Source app                         Notification Service                    Providers
      │                                        │                                 │
      │  POST /api/v1/notifications (JSON)     │                                 │
      │  + Idempotency-Key                     │                                 │
      │ ──────────────────────────────────────►│                                 │
      │                                        │ validate, idempotency, route    │
      │                                        │ persist QUEUED deliveries       │
      │  202 + notificationId + status         │                                 │
      │ ◄──────────────────────────────────────│                                 │
      │                                        │  DeliveryWorker (async claim)   │
      │                                        │ ────────────────────────────────►│
      │  GET /api/v1/notifications/{id}        │                                 │
      │ ──────────────────────────────────────►│                                 │
      │  status JSON (per recipient×channel)   │                                 │
      │ ◄──────────────────────────────────────│                                 │
```
| Path | Sync or async? | Owner |
|------|----------------|--------|
| Source → `POST /notifications` | **Synchronous HTTP** | Source REST client ↔ API layer |
| Delivery to Email/SMS/Slack | **Asynchronous** (after `202`) | Internal `DeliveryWorker` |
| Event-bus ingress (Kafka/Rabbit) | **Out of prototype scope** | Optional future edge — [Appendix B.5](#b5-production-evolution-optional-not-prototype) |
---

## 2. API contracts

### 2.1 Endpoints

| Method | Path | Success | Notes |
|--------|------|---------|-------|
| `POST` | `/api/v1/notifications` | `202 Accepted` (new); `200 OK` (idempotent replay) | Create / accept |
| `GET` | `/api/v1/notifications/{id}` | `200 OK` | Aggregate status |
| `GET` | `/api/v1/notifications/{id}/audit` | `200 OK` | Ordered audit events |

### 2.2 Headers

| Header | Required | Rules |
|--------|----------|-------|
| `Content-Type` | Yes on POST | `application/json` |
| `Idempotency-Key` | Recommended | 1–128 chars; unique per logical submit within retention with `sourceSystem` |
| `X-Correlation-Id` | Optional | If absent, server may echo `eventId` or generate |

### 2.3 `POST` request body (JSON schema — logical)

```json
{
  "sourceSystem": "billing-service",
  "eventId": "evt-9f3a",
  "correlationId": "corr-42",
  "type": "PAYMENT_FAILED",
  "severity": "HIGH",
  "priority": "NORMAL",
  "title": "Payment failed",
  "body": "Optional short text; prefer templateKey in production",
  "templateKey": "payment_failed_v1",
  "templateParams": { "amount": "42.00" },
  "recipients": [
    {
      "recipientId": "user-100",
      "email": "a@example.com",
      "phone": "+15551212",
      "slackUserOrChannel": "@alice",
      "preferredChannels": ["EMAIL", "SLACK"],
      "blockedChannels": ["SMS"]
    }
  ],
  "requestedChannels": ["EMAIL", "SMS", "SLACK"],
  "scheduleAt": null,
  "expiresAt": "2026-09-15T00:00:00Z"
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| `sourceSystem` | string | Yes | non-blank, ≤64 |
| `eventId` | string | Yes | non-blank, ≤128 |
| `correlationId` | string | No | ≤128; default = `eventId` |
| `type` | string | Yes | non-blank, ≤64 |
| `severity` | enum | Yes | `LOW` \| `MEDIUM` \| `HIGH` \| `CRITICAL` |
| `priority` | enum | Yes | `LOW` \| `NORMAL` \| `HIGH` |
| `recipients` | array | Yes | size 1..50 |
| `recipients[].recipientId` | string | Yes | non-blank |
| `recipients[].email/phone/slack…` | string | Cond. | At least one address matching a selectable channel |
| `requestedChannels` | array | No | subset of `EMAIL`, `SMS`, `SLACK`; empty → policy default |
| `scheduleAt` / `expiresAt` | ISO-8601 UTC | No | `expiresAt` > now; if both set, `expiresAt` > `scheduleAt` |
| `notificationId` | UUID string | No | If present must be unique; else server generates |

**Server-set on accept:** `id`, `createdAt`, `status`, `selectedChannels` (after routing).

### 2.4 `POST` response `202` / `200`

```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["EMAIL", "SLACK"],
  "createdAt": "2026-09-14T12:00:00Z"
}
```

Idempotent replay: same body as original accept; `duplicate` may be `false` for pure key replay (same resource) or status may reflect original. Content-dedup suppress may return `200` with `status: "DUPLICATE"` and `canonicalNotificationId`.

### 2.5 `GET` status response

```json
{
  "notificationId": "550e8400-e29b-41d4-a716-446655440000",
  "sourceSystem": "billing-service",
  "eventId": "evt-9f3a",
  "status": "IN_PROGRESS",
  "partialSuccess": false,
  "selectedChannels": ["EMAIL", "SLACK"],
  "createdAt": "2026-09-14T12:00:00Z",
  "updatedAt": "2026-09-14T12:00:05Z",
  "deliveries": [
    {
      "deliveryId": "...",
      "recipientId": "user-100",
      "channel": "EMAIL",
      "status": "SUCCEEDED",
      "attemptCount": 1,
      "lastAttemptAt": "2026-09-14T12:00:03Z",
      "lastErrorClass": null
    },
    {
      "deliveryId": "...",
      "recipientId": "user-100",
      "channel": "SLACK",
      "status": "RETRY_SCHEDULED",
      "attemptCount": 2,
      "nextAttemptAt": "2026-09-14T12:00:20Z",
      "lastErrorClass": "TRANSIENT_PROVIDER_FAILURE"
    }
  ]
}
```

### 2.6 Error model

```json
{
  "code": "VALIDATION_FAILED",
  "message": "recipients must not be empty",
  "correlationId": "corr-42",
  "details": [ { "field": "recipients", "reason": "must not be empty" } ]
}
```

| HTTP | When |
|------|------|
| `400` | Validation / expired already / no eligible channel at ingress |
| `404` | Unknown notification id |
| `409` | Same `Idempotency-Key` with **different** request hash |
| `500` | Unexpected server fault |

---

---

## 3. Package & class design

Base package: **`com.interview.assessment.notification`**

```text
com.interview.assessment.notification
├── controller
│   └── NotificationController
├── exception
│   └── GlobalExceptionHandler
├── dto
│   ├── SubmitNotificationRequest          // record
│   ├── RecipientDto                       // record
│   ├── NotificationAcceptResponse         // record
│   ├── NotificationStatusResponse         // record
│   ├── DeliveryStatusDto                  // record
│   ├── AuditEventDto                      // record (optional)
│   └── ErrorResponse                      // record (problem-style)
├── service
│   ├── NotificationIngestionService
│   ├── StatusQueryService
│   ├── DeliveryOrchestrator
│   ├── AuditService
│   ├── IdempotencyService
│   └── DedupHasher                        // or nested under IdempotencyService
├── domain
│   ├── enums
│   │   ├── NotificationStatus
│   │   ├── DeliveryStatus
│   │   ├── Channel
│   │   ├── Severity
│   │   ├── Priority
│   │   ├── FailureClass
│   │   └── AuditEventType                 // optional
│   ├── ChannelRouter
│   ├── RoutingPolicy
│   ├── RoutingInput / RoutingOutcome      // records or types
│   ├── RetryPolicy
│   └── FailureClassifier
├── strategy
│   ├── ChannelDeliveryStrategy            // interface
│   ├── ChannelStrategyRegistry
│   ├── EmailDeliveryStrategy
│   ├── SmsDeliveryStrategy
│   └── SlackDeliveryStrategy
├── persistence
│   ├── NotificationRepository
│   ├── DeliveryRepository
│   ├── IdempotencyRepository
│   ├── AuditRepository
│   └── sql / schema.sql
├── worker
│   └── DeliveryWorker                     // @Scheduled poll loop
└── config
    ├── JdbcConfig
    └── NotificationProperties             // backoff, TTL, channels
```

| Layer package | Responsibility |
|---------------|----------------|
| `controller` | REST endpoints only |
| `exception` | `@ControllerAdvice` / problem JSON mapping |
| `dto` | API request/response records (no business logic) |
| `service` | Use-case orchestration, transactions |
| `domain.enums` | Shared status/channel/severity/failure enums |
| `domain` | Routing, retry, classification policies |
| `strategy` | Channel delivery adapters (Strategy pattern) |
| `persistence` | JDBC repositories + SQL |
| `worker` | Async delivery poller |
| `config` | Spring beans / properties |

### 3.1 Core interfaces (signatures)

```java
package com.interview.assessment.notification.strategy;

public interface ChannelDeliveryStrategy {
    Channel channel();
    DeliveryResult deliver(DeliveryCommand command);
}

public record DeliveryCommand(
    UUID deliveryId,
    UUID notificationId,
    String recipientRef,
    Channel channel,
    String templateKey,
    Map<String, String> templateParams,
    int attemptNo
) {}

public record DeliveryResult(
    boolean success,
    FailureClass failureClass,   // null if success
    String providerRef,          // non-secret
    Integer providerHttpStatus,
    String safeDetail            // scrubbed
) {}

package com.interview.assessment.notification.domain;

public interface ChannelRouter {
    RoutingOutcome route(RoutingInput input);
}
```

### 3.2 Ingestion sequence (classes)

```text
controller.NotificationController
  → service.IdempotencyService.begin(source, key, requestHash)
  → (if hit) service.StatusQueryService / return existing
  → service.NotificationIngestionService.accept(cmd)
       → validate
       → domain.ChannelRouter.route
       → persistence repos insert notification, recipients, deliveries (QUEUED/PENDING)
       → service.IdempotencyService.commit mapping
       → service.AuditService.append(...)
  → 202 body (dto.NotificationAcceptResponse)
```

### 3.3 Delivery sequence (classes)

```text
worker.DeliveryWorker.tick()
  → persistence.DeliveryRepository.claimDue(batch, now, workerId)
  → for each claimed:
       service.DeliveryOrchestrator.process(delivery)
         → strategy.ChannelStrategyRegistry.get(channel).deliver(...)
         → domain.FailureClassifier / domain.RetryPolicy
         → update delivery + insert delivery_attempt
         → service.AuditService
         → roll up notification status
```

---

---

## 4. State machines (detailed)

### 4.1 Notification status transition matrix

| From \ To | ACCEPTED | ROUTED | QUEUED | IN_PROGRESS | COMPLETED | FAILED | REJECTED | DUPLICATE | EXPIRED |
|-----------|----------|--------|--------|-------------|-----------|--------|----------|-----------|---------|
| (new) | ✓ | | | | | | ✓ | ✓ | |
| ACCEPTED | | ✓ | | | | | ✓ | | ✓ |
| ROUTED | | | ✓ | | | | | | ✓ |
| QUEUED | | | | ✓ | | | | | ✓ |
| IN_PROGRESS | | | | | ✓ | ✓ | | | ✓ |
| terminal | | | | | | | | | |

**Guards (examples):**

- `→ REJECTED`: validation fail, `expiresAt` ≤ now at ingress, no eligible channel.  
- `→ DUPLICATE`: idempotency or content-hash hit (no new logical notification).  
- `→ QUEUED`: ≥1 delivery row created.  
- `→ IN_PROGRESS`: first claim or first attempt started.  
- `→ COMPLETED` / `FAILED` / `EXPIRED`: roll-up rules below.

### 4.2 Delivery status transition matrix

| From \ To | PENDING | IN_FLIGHT | RETRY_SCHEDULED | SUCCEEDED | FAILED_TERMINAL | EXPIRED |
|-----------|---------|-----------|-----------------|-----------|-----------------|---------|
| (new) | ✓ | | | | | |
| PENDING | | ✓ | | | | ✓ |
| IN_FLIGHT | | | ✓ | ✓ | ✓ | ✓ |
| RETRY_SCHEDULED | | ✓ | | | | ✓ |

**Claim guard:** `UPDATE … WHERE status IN ('PENDING','RETRY_SCHEDULED') AND next_attempt_at <= :now AND (schedule satisfied)`.

### 4.3 Overall status roll-up (authoritative for prototype)

Given all deliveries for a notification:

1. If notification `expiresAt` passed and no `SUCCEEDED` where required → `EXPIRED` (deliveries not yet terminal may be forced `EXPIRED`).  
2. Else if any delivery in `PENDING` | `IN_FLIGHT` | `RETRY_SCHEDULED` → `IN_PROGRESS`.  
3. Else if **all** `SUCCEEDED` → `COMPLETED`, `partialSuccess=false`.  
4. Else if **any** `SUCCEEDED` and rest `FAILED_TERMINAL` → `COMPLETED`, `partialSuccess=true`.  
5. Else all `FAILED_TERMINAL` → `FAILED`.

---

---

## 5. Physical data model (DDL sketch)

PostgreSQL-first; H2 `MODE=PostgreSQL` in tests. UUIDs as `UUID` (H2 compatible) or `VARCHAR(36)`.

```sql
CREATE TABLE notification (
    id                UUID PRIMARY KEY,
    source_system     VARCHAR(64)  NOT NULL,
    event_id          VARCHAR(128) NOT NULL,
    correlation_id    VARCHAR(128) NOT NULL,
    type              VARCHAR(64)  NOT NULL,
    severity          VARCHAR(16)  NOT NULL,
    priority          VARCHAR(16)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    partial_success   BOOLEAN      NOT NULL DEFAULT FALSE,
    template_key      VARCHAR(128),
    -- scrubbed/non-sensitive summary only; full body optional & minimized
    title             VARCHAR(256),
    schedule_at       TIMESTAMPTZ,
    expires_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    canonical_id      UUID         NULL REFERENCES notification(id)
);

CREATE TABLE notification_recipient (
    id               UUID PRIMARY KEY,
    notification_id  UUID NOT NULL REFERENCES notification(id),
    recipient_ref    VARCHAR(128) NOT NULL,
    email            VARCHAR(256),
    phone            VARCHAR(32),
    slack_target     VARCHAR(128),
    preferred_json   TEXT,
    blocked_json     TEXT
);

CREATE TABLE delivery (
    id                UUID PRIMARY KEY,
    notification_id   UUID NOT NULL REFERENCES notification(id),
    recipient_id      UUID NOT NULL REFERENCES notification_recipient(id),
    channel           VARCHAR(16) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    attempt_count     INT NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ NOT NULL,
    last_error_class  VARCHAR(64),
    provider_ref      VARCHAR(128),
    locked_by         VARCHAR(64),
    locked_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_delivery_claim
  ON delivery (status, next_attempt_at)
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED');

CREATE TABLE delivery_attempt (
    id                    UUID PRIMARY KEY,
    delivery_id           UUID NOT NULL REFERENCES delivery(id),
    attempt_no            INT  NOT NULL,
    started_at            TIMESTAMPTZ NOT NULL,
    finished_at           TIMESTAMPTZ,
    outcome               VARCHAR(32) NOT NULL,
    error_class           VARCHAR(64),
    provider_http_status  INT,
    provider_ref          VARCHAR(128),
    safe_detail           VARCHAR(512),
    UNIQUE (delivery_id, attempt_no)
);

CREATE TABLE idempotency_record (
    source_system     VARCHAR(64)  NOT NULL,
    idempotency_key   VARCHAR(128) NOT NULL,
    notification_id   UUID         NOT NULL REFERENCES notification(id),
    request_hash      CHAR(64)     NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    expires_at        TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (source_system, idempotency_key)
);

CREATE TABLE dedup_hash (
    hash             CHAR(64) PRIMARY KEY,
    notification_id  UUID NOT NULL REFERENCES notification(id),
    created_at       TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_event (
    id               UUID PRIMARY KEY,
    notification_id  UUID,
    delivery_id      UUID,
    event_type       VARCHAR(64) NOT NULL,
    payload_json     TEXT NOT NULL,  -- scrubbed only
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_notification ON audit_event (notification_id, created_at);
```

### 5.1 Request hash & content hash

- **Idempotency request hash:** SHA-256 over canonical JSON of the POST body (stable key order), hex-encoded.  
- **Content dedup hash:** SHA-256 over `sourceSystem|eventId|type|severity|normalizedRecipientIds|sortedChannels`.  
- **Retention:** `expires_at = created_at + 24 hours` (configurable).

---

---

## 6. Asynchronous worker (detailed)

### 6.1 Poll loop

| Property | Default |
|----------|---------|
| Fixed delay / cron | every 1–2s (config) |
| Batch size | 10–50 |
| Lock timeout | re-claim if `locked_at` older than 2 minutes (crash recovery) |

### 6.2 Claim semantics (pseudo-SQL)

```sql
UPDATE delivery
SET status = 'IN_FLIGHT',
    locked_by = :workerId,
    locked_at = :now,
    updated_at = :now,
    attempt_count = attempt_count + 1
WHERE id IN (
  SELECT id FROM delivery
  WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
    AND next_attempt_at <= :now
    AND (locked_at IS NULL OR locked_at < :lockStaleBefore)
  ORDER BY next_attempt_at
  FOR UPDATE SKIP LOCKED
  LIMIT :batch
);
```

(H2 may approximate `SKIP LOCKED` with single-threaded worker in tests.)

### 6.3 Schedule & expiry

- Claim only if `notification.schedule_at IS NULL OR schedule_at <= now`.  
- If `expires_at <= now` before success → mark delivery `EXPIRED`, audit, roll up; **do not** call provider.

---

---

## 7. Routing algorithm

```text
function route(req, recipient):
  channels = req.requestedChannels
             ?? policy.defaultChannelsFor(req.severity)

  channels = channels ∩ enabledChannels ∩ supportedByAddresses(recipient)
  channels = channels \ recipient.blockedChannels

  if recipient.preferredChannels not empty:
    // soft preference: stable-sort preferred first; do not drop others unless policy says
    channels = orderByPreferred(channels, recipient.preferredChannels)

  if req.severity == CRITICAL:
    ensureIntersects(channels, policy.fastChannels)  // e.g. SMS, SLACK
    // if still empty after ensure → fail route

  if channels empty:
    return Reject(NO_ELIGIBLE_CHANNEL)

  return Selected(channels)  // frozen on notification + one delivery row each
```

Audit `ROUTING_DECISION` with `{ recipientId, selected[], reasonCodes[] }` — no secrets.

---

---

## 8. Idempotency & deduplication (procedural)

```text
txn:
  if Idempotency-Key present:
    existing = find(source, key)
    if existing and not expired:
      if existing.request_hash != hash(body): abort 409
      else return existing.notification  // 200 replay
    // else insert placeholder or proceed under unique constraint

  if contentHash exists and not expired:
    audit DUPLICATE_SUPPRESSED
    return DUPLICATE pointing at canonical  // 200

  create notification + children
  insert idempotency_record + dedup_hash with expires_at = now+24h
commit
```

Unique `(source_system, idempotency_key)` enforces concurrency safety; on conflict, re-read and apply hash check.

---

---

## 9. Retry & failure classification

### 9.1 Classifier mapping (adapter → domain)

| Provider signal | `FailureClass` | Retry |
|-----------------|----------------|-------|
| HTTP 5xx, connection reset | `TRANSIENT_PROVIDER_FAILURE` | Yes |
| Socket/read timeout | `TIMEOUT` | Yes |
| HTTP 429 | `RATE_LIMIT` | Yes (+ `Retry-After` if present) |
| HTTP 400 business | `PERMANENT_PROVIDER_REJECTION` | No |
| Unknown user / bounce | `INVALID_RECIPIENT` | No |
| HTTP 401/403 | `AUTH_ERROR` | No |
| Unmapped | `UNKNOWN` | Yes, strict cap (same max attempts) |

### 9.2 Backoff formula

```text
maxAttempts = 5
baseMs = 1000
capMs  = 300_000
attempt = delivery.attempt_count   // after increment

if failure not retryable OR attempt >= maxAttempts:
  status = FAILED_TERMINAL
else:
  exp = min(capMs, baseMs * 2^(attempt - 1))
  delay = randomUniform(0, exp)     // full jitter
  if RATE_LIMIT and retryAfterMs present:
    delay = max(delay, retryAfterMs)
  next_attempt_at = now + delay
  status = RETRY_SCHEDULED
```

---

---

## 10. Audit events (payload shapes)

| `event_type` | `payload_json` (example keys only) |
|--------------|--------------------------------------|
| `NOTIFICATION_ACCEPTED` | `sourceSystem`, `eventId`, `severity` |
| `NOTIFICATION_REJECTED` | `reasonCode` |
| `DUPLICATE_SUPPRESSED` | `canonicalNotificationId`, `mechanism` |
| `ROUTING_DECISION` | `recipientId`, `selectedChannels`, `reasons` |
| `DELIVERY_QUEUED` | `deliveryId`, `channel` |
| `DELIVERY_ATTEMPTED` | `attemptNo`, `channel` |
| `DELIVERY_SUCCEEDED` | `attemptNo`, `providerRef` |
| `DELIVERY_FAILED` | `attemptNo`, `failureClass` |
| `RETRY_SCHEDULED` | `attemptNo`, `nextAttemptAt` |
| `RETRY_EXHAUSTED` | `attemptNo`, `failureClass` |
| `NOTIFICATION_COMPLETED` / `FAILED` / `EXPIRED` | `partialSuccess?` |

**Never:** raw body PII dumps, tokens, webhook URLs with secrets, `Authorization` headers.

---

---

## 11. Channel strategy details (prototype)

| Channel | Address field | Prototype behavior |
|---------|---------------|--------------------|
| `EMAIL` | `email` | Log/simulate send; optional JavaMail later |
| `SMS` | `phone` | Stub client; map faults via test doubles |
| `SLACK` | `slack_target` | Incoming webhook or stub; brownfield add |

Each strategy:

1. Validates address present.  
2. Invokes provider (or stub).  
3. Maps response → `DeliveryResult` with `FailureClass`.  
4. Must not log secrets.

`ChannelStrategyRegistry`: `Map<Channel, ChannelDeliveryStrategy>`; unknown channel → fail closed at route time.

---

---

## 12. Configuration keys

```yaml
notification:
  idempotency-ttl: 24h
  dedup-ttl: 24h
  worker:
    fixed-delay-ms: 1000
    batch-size: 20
    lock-stale-ms: 120000
  retry:
    max-attempts: 5
    base-delay-ms: 1000
    max-delay-ms: 300000
  routing:
    default-channels: [EMAIL]
    fast-channels: [SMS, SLACK]
    critical-requires-fast: true
  channels:
    email.enabled: true
    sms.enabled: true
    slack.enabled: true
```

---

---

## 13. Transaction boundaries

| Use case | Boundary |
|----------|----------|
| Accept notification | Single TX: idempotency check/insert, notification, recipients, deliveries, audit accept/route/queue |
| Claim batch | TX per claim UPDATE; process delivery preferably **after** commit of claim (avoid long provider I/O in DB TX) |
| Finish attempt | TX: attempt row, delivery status, audit, notification roll-up |
| Idempotent POST replay | Read-only TX or none |

**Rule:** Never hold DB locks while calling Email/SMS/Slack.

---

---

## 14. Test matrix (LLD-level)

| Case | Expect |
|------|--------|
| Valid POST | 202, rows QUEUED, audit accepted/routed/queued |
| Replay same key + body | 200, same id, no second notification |
| Same key + different body | 409 |
| Content dedup within 24h | DUPLICATE / canonical pointer |
| CRITICAL + blocked fast channels | reject or fallback per policy (document in test) |
| Stub 500 then 200 | RETRY_SCHEDULED then SUCCEEDED |
| Invalid recipient | FAILED_TERMINAL, no retry |
| Expiry before claim | EXPIRED, no provider call |
| Partial channel success | COMPLETED + partialSuccess=true |
| Worker double-claim | one winner via conditional update |

---

---

## 15. Traceability (LLD)

| Requirement | LLD sections |
|-------------|--------------|
| 4.1 Submit | §1, §2, §3.2, §13 |
| 4.2 Status | §2.5, §4.3 |
| 4.3 Routing | §7 |
| 4.4 Dedup / idempotency | §5.1, §8, Appendix B |
| 4.5 Retry | §6, §9 |
| 4.9 Audit | §10 |
| Async processing | §1, §6 |
| Brownfield Strategy + Slack | §3.1, §11 |
---

## Appendix A — Document map (HLD vs LLD)

Orientation only: what belongs in the HLD versus this LLD.
| Concern | HLD | LLD (this doc) |
|---------|-----|----------------|
| Context & layers | Yes | Reference only |
| How sources call us | One-line: REST/JSON | §1 sequence + Appendix B |
| API field lists / JSON schemas | Capability summary | §2 Exact request/response, validation, HTTP codes |
| State model | Named states & intent | §4 Transition matrix, guards, roll-up rules |
| Data model | Logical entities | §5 Physical DDL, indexes, constraints |
| Async | Worker + queue concept | §6 Claim SQL, poll loop, locking |
| Patterns | Strategy / Gateway named | §3 Interfaces, packages, method signatures |
---

## Appendix B — Client integration narrative

Supporting detail for assessment Q&A. Design contracts remain in §1–§2.
### B.1 Integration style (decision)

**Primary ingress is synchronous REST/JSON.** Source applications are **HTTP clients** of this service. They do **not** publish onto an event bus *into* this prototype.
| Concern | Choice |
|---------|--------|
| Protocol | HTTPS |
| Content-Type | `application/json` |
| Submit | `POST /api/v1/notifications` |
| Status | `GET /api/v1/notifications/{id}` |
| Audit (optional) | `GET /api/v1/notifications/{id}/audit` |
| Client tech | Any REST client (`RestClient` / `WebClient`, Feign, curl, …) |
| Recommended header | `Idempotency-Key: <opaque-string>` |
| Coupling | Source knows base URL, JSON schema, and auth (when enabled) |
### B.2 What “event” means (and does not)

| Concept | Meaning in this design |
|---------|------------------------|
| `eventId` / `correlationId` | Fields **inside** the REST JSON body for cross-system correlation |
| Internal async worker | Off-request-thread delivery after accept (§6) |
| Event-driven **ingress** | Not required; future consumer may call the same `NotificationIngestionService` as the REST controller |
### B.3 Source-system consumption flow

1. Business/technical condition arises in the source app.  
2. Source builds notification JSON (`sourceSystem`, `eventId`, severity, recipients, channels, …).  
3. Source calls `POST /api/v1/notifications` with `Idempotency-Key`.  
4. This service validates, persists, routes, enqueues deliveries; returns **`202 Accepted`** + `notificationId`.  
5. Source (or ops) may poll `GET /api/v1/notifications/{id}` for outcomes.
### B.4 Example: source app calling with Spring `RestClient`

```java
restClient.post()
    .uri("https://notification-service/api/v1/notifications")
    .header("Idempotency-Key", idempotencyKey)
    .contentType(MediaType.APPLICATION_JSON)
    .body(request)
    .retrieve()
    .toEntity(NotificationAcceptResponse.class);
```
### B.5 Production evolution (optional, not prototype)

A thin Kafka/Rabbit consumer may map messages → `NotificationIngestionService` (same application service as the controller). Domain, JDBC, and worker remain unchanged; only the edge multiplies.
---
*End of LLD.*
