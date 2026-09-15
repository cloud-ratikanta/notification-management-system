# Architecture — Notification Management Service

> **Document type:** High-Level Design (HLD)  
> **Audience:** Engineers, reviewers, and operators  
> **Stack:** Java 21 · Spring Boot 3 · Spring JDBC · PostgreSQL / H2  
> **Related:** [`lld.md`](lld.md) (detailed design) · [`approach.md`](approach.md) · [`README.md`](../README.md)

---

## 1. Purpose & Design Goals

The Notification Management Service receives alert requests from business and technical systems and delivers notifications to users through configurable channels (Email, SMS, Slack, and future adapters).

This prototype is designed as though it could evolve into a production service, while remaining manageable and runnable within a 2–3 day delivery window.

| Goal | Design response |
|------|-----------------|
| Clear domain boundaries | Layered controller → service → routing/dedup → strategy adapters → JDBC persistence |
| Safe evolution | Strategy-based channel adapters; additive schema; stable public API contracts |
| Operational safety | Idempotency keys, bounded retries, audit without sensitive payload leakage |
| Runnable prototype | Single Spring Boot app; async delivery via in-process queue + worker; H2 for tests, PostgreSQL for runtime |
| Reviewability | Explicit state model, failure taxonomy, retention policies; implementation detail in LLD |

**HLD vs LLD:** This document defines *what* the system is and *why* major boundaries exist. Client call patterns, JSON schemas, sequences, DDL, transition matrices, and class-level flows live in [`lld.md`](lld.md).

---

## 2. Scope & Engineering Scenarios

The architecture deliberately maps to three assessment scenarios.

### 2.1 Greenfield — Core capability

Initial vertical slice:

- Notification submission API  
- Recipient and channel selection (routing)  
- Asynchronous processing of delivery work  
- Delivery attempts against channel providers  
- Status retrieval API  

### 2.2 Brownfield — Multi-layer enhancement

Treat the greenfield system as “existing” and evolve it without breaking contracts:

- New channel adapter (Slack)  
- Deduplication / idempotency gateway  
- Refactor of provider-specific logic into a **Strategy** interface so Email/SMS/Slack share one delivery pipeline  

### 2.3 Ambiguous requirements — Explicit engineering choices

Vague asks such as *“handle retries safely”* and *“prevent avoidable duplicates”* are resolved into:

- Failure classification taxonomy (transient vs permanent, etc.)  
- Bounded exponential backoff with jitter and max attempts  
- 24-hour idempotency / content-hash retention window  
- Audit events that record outcomes without storing credentials or full message bodies  

---

## 3. Context Diagram

Source applications integrate as **HTTP clients** (REST + JSON). Ingress is not event-bus-based in the prototype. Delivery to providers after accept is **internal async**. Client consumption steps, sequences, RestClient examples, and “event vs REST” semantics are specified in [`lld.md` §2](lld.md).

```text
┌──────────────────────────┐
│  Source applications     │
│  (billing, monitoring,   │
│   ops tools, other APIs) │
└────────────┬─────────────┘
             │
             │  HTTPS + JSON (REST)
             │  POST/GET /api/v1/notifications
             ▼
┌──────────────────────────────────────────────┐
│  Notification Management Service             │
│  Ingest · Route · Queue · Deliver            │
│  Idempotency · Retry · Audit                 │
└──────────────────────┬───────────────────────┘
                       │
    ┌──────────────────┼──────────────────┐
    ▼                  ▼                  ▼
┌─────────┐      ┌─────────┐       ┌─────────┐
│ Email   │      │ SMS     │       │ Slack   │
│ adapter │      │ adapter │       │ adapter │
└─────────┘      └─────────┘       └─────────┘
    ▲
    │ persistence
┌───┴────────┐
│ PostgreSQL │  (H2 MODE=PostgreSQL in tests)
└────────────┘
```

| Concern | Pattern (HLD) |
|---------|----------------|
| Source → service | Synchronous REST/JSON |
| Service → Email/SMS/Slack | Asynchronous worker after accept |
| Event-bus ingress | Out of prototype scope (optional later edge) |

**Trust boundary:** The service is the sole writer of notification state. Channel providers are untrusted external I/O; adapters normalize their responses into a common failure/success model.

---

## 4. Logical Architecture

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                         API / Edge Layer                                   │
│  Controllers · Bean Validation · Idempotency-Key header · Error mapping    │
└───────────────────────────────┬────────────────────────────────────────────┘
                                │
┌───────────────────────────────▼────────────────────────────────────────────┐
│                      Service layer                                  │
│  NotificationIngestionService · StatusQueryService · DeliveryOrchestrator  │
└───────┬─────────────────┬─────────────────┬────────────────┬───────────────┘
        │                 │                 │                │
        ▼                 ▼                 ▼                ▼
┌───────────────┐ ┌───────────────┐ ┌──────────────┐ ┌─────────────────────┐
│ Idempotency & │ │ Channel       │ │ Retry /      │ │ AuditRecorder       │
│ Dedup Gateway │ │ Router        │ │ Backoff      │ │ (privacy-scrubbed)  │
└───────┬───────┘ └───────┬───────┘ └──────┬───────┘ └──────────┬──────────┘
        │                 │                │                    │
        │         ┌───────▼────────┐       │                    │
        │         │ ChannelStrategy│◄──────┘                    │
        │         │  Email|SMS|    │  DeliveryWorker (async)    │
        │         │  Slack|…       │                            │
        │         └───────┬────────┘                            │
        ▼                 ▼                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│              Persistence (NamedParameterJdbcTemplate + raw SQL)           │
│  notifications · recipients · delivery_attempts · idempotency_keys · audit│
└───────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Layer responsibilities

| Layer | Responsibility | Does not |
|-------|----------------|----------|
| **Controller / DTO / Exception** | HTTP semantics, validation, idempotency key intake, error mapping | Business routing rules, provider I/O |
| **Service** | Use-case orchestration, transactions around accept/queue, status aggregation | Provider SDK details |
| **Domain (+ enums)** | Routing, dedup boundary, retry classification, state transitions | HTTP, SQL dialects |
| **Strategy** | Map domain delivery intent → provider call; map provider errors → failure class | Persistence of core notification rows |
| **Persistence** | Durable state, uniqueness constraints, audit append | Channel protocol logic |

### 4.2 Key design patterns

| Pattern | Where | Why |
|---------|--------|-----|
| **Strategy** | `ChannelDeliveryStrategy` per channel | Brownfield: add Slack without branching in orchestrator |
| **Factory / registry** | Strategy lookup by channel enum | O(1) selection; fail closed on unknown channel |
| **Gateway** | Idempotency & deduplication | Single choke point before “create notification” |
| **Outbox-style work items** | Delivery queue table (or status=`QUEUED` + worker poll) | Survive process restart; avoid fire-and-forget only |
| **Records (Java 21)** | DTOs / immutable domain snapshots | Clear contracts, less boilerplate |

---

## 5. Runtime & Deployment View

| Environment | Runtime | Data store | Notes |
|-------------|---------|------------|--------|
| Local / CI | Spring Boot fat JAR, JDK 21 | H2 in-memory `MODE=PostgreSQL` | Fast tests; SQL kept portable |
| PCF / Tanzu | `cf push` via `manifest.yml` | Managed PostgreSQL service | Binding via Spring Cloud / env |
| Async model (prototype) | In-app worker + virtual threads (optional) | DB-backed delivery jobs | Production evolution: replace with message broker (Rabbit/Kafka) without changing domain API |

**Evolution path:** Keep the *delivery job* abstraction stable. Swap the transport (in-process → broker) behind the same `DeliveryOrchestrator` interface.

---

## 6. Functional Capability Mapping

### 6.1 Submit a notification (FR 4.1)

**API (illustrative):** `POST /api/v1/notifications`

**Request payload (logical fields):**

| Field | Required | Description |
|-------|----------|-------------|
| `notificationId` | Optional* | Client- or server-generated unique id (*server generates if omitted) |
| `sourceSystem` | Yes | Originating system identity |
| `eventId` / `correlationId` | Yes | Cross-system correlation |
| `type` | Yes | Notification type / template key |
| `severity` | Yes | e.g. `LOW` · `MEDIUM` · `HIGH` · `CRITICAL` |
| `priority` | Yes | Processing/order hint relative to peers |
| `recipients[]` | Yes (≥1) | Identity + optional per-recipient channel prefs |
| `requestedChannels[]` | Optional | Client-eligible or preferred channels |
| `createdAt` | Server-set | Creation timestamp (UTC) |
| `scheduleAt` | Optional | Defer enqueue until |
| `expiresAt` | Optional | Do not deliver after |
| Headers: `Idempotency-Key` | Strongly recommended | Stable key for safe retries of the HTTP call |

**Accept path (happy path):**

1. Validate payload.  
2. Resolve idempotency / content dedup (see §8).  
3. Persist notification + recipients in `ACCEPTED` (or equivalent).  
4. Run channel router → selected channels.  
5. Create delivery work items → `QUEUED`.  
6. Append audit: accepted, routed, queued.  
7. Return `202 Accepted` (or `200` on idempotent replay) with notification id and current status.

### 6.2 Notification status (FR 4.2)

**API (illustrative):** `GET /api/v1/notifications/{id}`

**Response aggregates:**

- Overall notification status (derived — see §7)  
- Selected channels  
- Per recipient × channel delivery status  
- Timestamps: created, routed, last attempt, delivered/failed/expired  

### 6.3 Channel routing (FR 4.3)

Routing is a pure policy function evaluated at accept time (and re-evaluable only if explicitly designed for preference changes; prototype freezes selection at accept).

**Inputs:**

- Requested / eligible channels  
- Notification severity & priority  
- Recipient preferences (if present)  
- Global routing policy (config)

**Example decision rules (defensible defaults):**

| Condition | Outcome |
|-----------|---------|
| Client requested channels ∩ supported channels | Start from intersection |
| Severity `CRITICAL` | Ensure at least one “fast” channel (e.g. SMS or Slack) if recipient allows |
| Recipient opted out of a channel | Drop that channel; audit suppression |
| No channel remains | Mark notification `REJECTED` or `FAILED` with reason `NO_ELIGIBLE_CHANNEL` |
| `expiresAt` already past | Reject at ingress |

Selected channels are stored so status and audit remain explainable after policy config changes.

### 6.4 Deduplication & idempotency (FR 4.4)

See §8.

### 6.5 Retry & failure handling (FR 4.5)

See §9.

### 6.6 Audit history (FR 4.9)

See §10.

---

## 7. State Model

A two-level model separates **notification** lifecycle from **delivery attempt** lifecycle. This is deliberate: overall status is derived from child deliveries so partial success is visible.

### 7.1 Notification-level states

```text
                  ┌──────────┐
     reject       │ REJECTED │
   ┌─────────────►│ (terminal)│
   │              └──────────┘
   │
 ACCEPTED ──► ROUTED ──► QUEUED ──► IN_PROGRESS ──► COMPLETED
   │                        │              │              │
   │                        │              │              ├─ all required deliveries terminal
   │                        │              │              │
   │                        │              └──► FAILED (terminal: unrecoverable / exhausted)
   │                        │
   │                        └──► EXPIRED (schedule/expiry window missed)
   │
   └── DUPLICATE (terminal logical: suppressed; points to original id)
```

| State | Meaning |
|-------|---------|
| `ACCEPTED` | Validated and persisted; not yet fully routed |
| `ROUTED` | Channels chosen and frozen |
| `QUEUED` | Delivery jobs waiting for worker |
| `IN_PROGRESS` | At least one delivery attempt active or pending retry |
| `COMPLETED` | All delivery targets reached a successful terminal state (or acceptable partial policy) |
| `FAILED` | Terminal failure after policy (e.g. all permanent failures or retries exhausted) |
| `REJECTED` | Ingress or routing refused (validation, no channels, authz) |
| `DUPLICATE` | Suppressed by idempotency/dedup; status references canonical notification |
| `EXPIRED` | Past `expiresAt` before successful delivery |

**Overall status derivation (default policy):**

- If any delivery still retryable / queued → `IN_PROGRESS`  
- If all succeeded → `COMPLETED`  
- If mixed success/permanent fail → `COMPLETED` with `partialSuccess=true` **or** `FAILED` if policy requires all-or-nothing (document choice in LLD; prototype recommends **partial success visible**)  
- If notification expired → `EXPIRED`

### 7.2 Delivery (recipient × channel) states

```text
PENDING ──► IN_FLIGHT ──► SUCCEEDED
                │
                ├──► RETRY_SCHEDULED ──► IN_FLIGHT (bounded)
                │
                └──► FAILED_TERMINAL
```

Each transition records attempt number, failure class, provider reference (non-secret), and timestamps.

---

## 8. Deduplication & Idempotency

### 8.1 Two complementary boundaries

| Mechanism | Key material | Prevents | Client-visible behavior |
|-----------|--------------|----------|-------------------------|
| **HTTP idempotency** | `Idempotency-Key` + `sourceSystem` (and optionally route) | Double-create from retried POST | Same notification id + status; HTTP 200/409-with-body of original |
| **Content deduplication** | Hash of `(sourceSystem, eventId, type, normalized recipients, severity, …)` | Avoidable duplicate alerts from chatty producers | Suppress or link as `DUPLICATE`; audit `DUPLICATE_SUPPRESSED` |

### 8.2 Processing guarantees

1. **Same idempotency key** → must not create a second *logical* notification. Enforce with a unique DB constraint on `(source_system, idempotency_key)` and a transactional “insert-or-return existing” path.  
2. **Reprocessing a queued delivery** → worker uses optimistic locking / `UPDATE … WHERE status = PENDING/RETRY` so concurrent pollers do not double-send. Attempt rows are append-only.  
3. **Duplicates** → always reflected in status (`DUPLICATE` or replay of original) and audit.  
4. **Side effects** → channel adapters are invoked only after claim of a delivery attempt id; provider callbacks (if any) key off attempt id.

### 8.3 Retention policy (ambiguous requirement → explicit)

| Store | Retention | Rationale |
|-------|-----------|-----------|
| Idempotency keys | **24 hours** from first accept (configurable) | Covers typical producer retry windows without unbounded growth |
| Content dedup hashes | **24 hours** | Same window; aligned ops story |
| Notification & delivery rows | Longer (product/compliance); prototype: retain for app lifetime / explicit purge job later | Status APIs remain useful |
| Audit events | Align with notification retention; no message body/credentials | Privacy |

After retention expiry, a reused idempotency key may create a new notification; producers should treat keys as time-scoped.

---

## 9. Retry & Failure Handling

### 9.1 Failure taxonomy

Every provider error is mapped into exactly one class:

| Class | Examples | Retry? |
|-------|----------|--------|
| `TRANSIENT_PROVIDER_FAILURE` | 5xx, connection reset | Yes, bounded |
| `TIMEOUT` | Read/connect timeout | Yes, bounded |
| `RATE_LIMIT` | HTTP 429; `Retry-After` honored when present | Yes, bounded (respect server hint) |
| `PERMANENT_PROVIDER_REJECTION` | 400 business reject from provider | No |
| `INVALID_RECIPIENT` | Bounced address, unknown MSISDN, invalid Slack channel | No |
| `AUTH_ERROR` | 401/403 to provider (bad token/config) | No automatic recipient retry; alert ops (config fix) |
| `UNKNOWN` | Unmapped | Conservative: treat as transient with lower max attempts, or permanent after 1 — **prototype: transient with strict cap** |

### 9.2 Bounded backoff policy (explicit SLA-style defaults)

| Parameter | Default (prototype) |
|-----------|---------------------|
| Max attempts per delivery | 5 |
| Backoff | Exponential: `baseMs * 2^(attempt-1)` |
| Jitter | Full jitter in `[0, backoff]` |
| Base delay | 1s |
| Max delay cap | 5 minutes |
| Give-up | Mark `FAILED_TERMINAL`; roll up notification status; audit `RETRY_EXHAUSTED` |

**Auth errors** and **invalid recipient** skip the retry ladder and fail fast.

### 9.3 Distinction from submission retries

- Client **POST** retries → idempotency key (§8).  
- **Delivery** retries → attempt counter + backoff (§9).  
These must not be conflated: re-POSTing must not reset delivery attempt budgets on an existing notification.

---

## 10. Audit History

### 10.1 Significant events (minimum set)

| Event | When |
|-------|------|
| `NOTIFICATION_ACCEPTED` | Ingress success |
| `NOTIFICATION_REJECTED` | Validation / policy refuse |
| `DUPLICATE_SUPPRESSED` | Idempotent or content dedup hit |
| `ROUTING_DECISION` | Channels selected (store channel list + brief reason codes, not secrets) |
| `DELIVERY_QUEUED` | Work item created |
| `DELIVERY_ATTEMPTED` | Adapter invoked |
| `DELIVERY_SUCCEEDED` | Provider ack |
| `DELIVERY_FAILED` | Failure class + attempt # |
| `RETRY_SCHEDULED` | Next attempt time |
| `NOTIFICATION_COMPLETED` / `FAILED` / `EXPIRED` | Terminal roll-up |

### 10.2 Privacy rules

Audit **must not** store:

- Full free-text message bodies when avoidable (store template id + non-sensitive tokens only)  
- Credentials, API tokens, webhook secrets  
- Raw provider authorization headers  

Prefer: notification id, recipient **references** (hashed or internal ids where possible), channel, event codes, timestamps, correlation/event ids.

---

## 11. Data Model (logical)

```text
NOTIFICATION
  id, source_system, event_id, type, severity, priority,
  status, created_at, schedule_at, expires_at, correlation fields…

NOTIFICATION_RECIPIENT
  id, notification_id, recipient_ref, preference snapshot…

DELIVERY
  id, notification_id, recipient_id, channel, status,
  attempt_count, next_attempt_at, last_error_class, provider_ref…

DELIVERY_ATTEMPT  (append-only)
  id, delivery_id, attempt_no, started_at, finished_at,
  outcome, error_class, provider_response_code (no secrets)

IDEMPOTENCY_RECORD
  source_system, idempotency_key, notification_id, request_hash, created_at, expires_at

DEDUP_HASH (optional separate from idempotency)
  hash, notification_id, created_at, expires_at

AUDIT_EVENT
  id, notification_id?, delivery_id?, event_type, payload_json (scrubbed), created_at
```

**Integrity:** Unique `(source_system, idempotency_key)` while unexpired; FK cascades controlled so audit remains even if operational purge is introduced later.

---

## 12. API Surface (summary)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/notifications` | Submit notification (`Idempotency-Key` header) |
| `GET` | `/api/v1/notifications/{id}` | Overall + per-channel/recipient status |
| `GET` | `/api/v1/notifications/{id}/audit` | Optional audit trail (prototype-friendly) |

**Error model:** Problem-style JSON (`code`, `message`, `correlationId`); map validation → 400, missing → 404, conflict on incompatible idempotent payload → 409.

---

## 13. Asynchronous Processing

```text
  POST accept (txn)
       │
       ├─ persist notification + deliveries (QUEUED)
       ├─ audit
       └─ commit  ──►  DeliveryWorker polls / claims due rows
                            │
                            ├─ mark IN_FLIGHT
                            ├─ ChannelStrategy.deliver()
                            ├─ success → SUCCEEDED
                            └─ failure → classify → RETRY_SCHEDULED or FAILED_TERMINAL
```

**Prototype choice:** DB-scheduled worker (simple, crash-safe enough for demo).  
**Production evolution:** external broker + competing consumers; same claim/attempt semantics.

Scheduling: if `scheduleAt` is future, deliveries become claimable only after that instant; if `expiresAt` passes, worker marks expired without calling provider.

---

## 14. Component Catalog (target package shape)

Base: **`com.interview.assessment.notification`** (detail in [`lld.md` §3](lld.md))

```text
controller/     NotificationController
exception/      GlobalExceptionHandler
dto/            request/response records
service/        ingestion, status, orchestration, audit, idempotency
domain/
  enums/        NotificationStatus, DeliveryStatus, Channel, Severity, ...
  ...           ChannelRouter, RoutingPolicy, RetryPolicy, FailureClassifier
strategy/       ChannelDeliveryStrategy + Email/Sms/Slack implementations
persistence/    JDBC repositories, SQL, row mappers
worker/         delivery poller / claim loop
config/         datasource, threading, backoff properties
```

---

## 15. Cross-Cutting Concerns

| Concern | Approach |
|---------|----------|
| **Validation** | Jakarta Validation on API records; domain asserts on state transitions |
| **Time** | UTC timestamps only |
| **Config** | `application.yml`: backoff, retention TTL, enabled channels, provider endpoints |
| **Observability** | Structured logs with `notificationId`, `correlationId`, `deliveryId`; metrics counters for accepts, duplicates, retries, failures by class |
| **Security** | No secrets in audit/logs; provider credentials via env/PCF service binding; least-privilege DB role |
| **Testing** | H2 PostgreSQL mode; unit tests for router/retry classifier; integration tests for idempotency and state roll-up |

---

## 16. Brownfield Evolution Path

Order of change that protects the greenfield core:

1. Introduce `ChannelDeliveryStrategy` and move Email/SMS bodies behind it (behavior-preserving refactor).  
2. Add Slack strategy + config flag; router whitelist includes `SLACK`.  
3. Add idempotency gateway + unique constraint + 24h purge/TTL.  
4. Tighten retry taxonomy and audit events without changing external status field names (additive enums only).

Public API remains backward compatible; new optional headers/fields only.

---

## 17. Out of Scope (prototype)

- Full multi-region active-active  
- End-user preference microservice UI  
- Guaranteed exactly-once *provider* side effects (industry reality is at-least-once + idempotent providers); we document and mitigate  
- Complex template rendering CMS  
- Real SMS/Email vendor accounts (adapters may stub or use test doubles in CI)  
- Event-bus **ingress** as the primary API (optional later; see LLD §2.6)

---

## 18. Decisions Log (ADR-style summary)

| ID | Decision | Rationale |
|----|----------|-----------|
| D1 | Spring JDBC over JPA | Explicit SQL, clearer performance story, matches assessment stack |
| D2 | Strategy per channel | Brownfield extensibility without orchestrator churn |
| D3 | DB-backed queue for prototype | Runnable without broker ops; restart-safe enough |
| D4 | 24h idempotency/dedup retention | Concrete answer to ambiguous “prevent duplicates” |
| D5 | Two-level state model | Accurate partial delivery; defensible vs single flat enum |
| D6 | Failure taxonomy table | Makes “safe retries” reviewable and testable |
| D7 | Scrubbed audit | Satisfies FR 4.9 privacy without losing operability |
| D8 | REST/JSON ingress (not event bus) | Simple, runnable client integration; details in LLD §2 |

---

## 19. Traceability Matrix

| Requirement | HLD | LLD |
|-------------|-----|-----|
| 4.1 Submit | §6, §12 | §2, §3, §4 |
| 4.2 Status | §6, §7 | §3.5, §5 |
| 4.3 Routing | §6 | §8 |
| 4.4 Dedup / idempotency | §8 | §9 |
| 4.5 Retry / failures | §9 | §10 |
| 4.9 Audit | §10 | §11 |
| Greenfield / async | §2.1, §13 | §2.4, §7 |
| Brownfield | §2.2, §16 | §4, §12 |
| Client integration | §3 (summary) | §2 (full) |
| Ambiguous reqs | §2.3, §8–9 | §9–10 |

---

## 20. Next Documents

1. **[`lld.md`](lld.md)** — client integration, API schemas, DDL, state matrices, algorithms (authoritative for implementation).  
2. **`docs/stories/`** — epics/stories per greenfield, brownfield, and ambiguous phases.  
3. Implementation follows HLD boundaries + LLD specs; deviations update the appropriate doc.

---

*End of architecture document.*
