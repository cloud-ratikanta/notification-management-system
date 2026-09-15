# Epic AR — Ambiguous requirements → explicit policies
> **Scenario:** 3.3 Ambiguous requirements  
> **HLD:** §2.3, §8.3, §9  
> **LLD:** §6, §9–§11, §12  
Turn vague asks (“handle retries safely”, “prevent avoidable duplicates”) into **testable SLAs**.
---
## AR-01 — Failure taxonomy & bounded retry policy
**As a** platform owner  
**I want** every provider failure classified and retried only when appropriate  
**So that** “safe retries” is reviewable and not infinite  
### Acceptance criteria
- [ ] `domain.FailureClassifier` maps signals → `FailureClass` per LLD §9.1  
- [ ] Retryable: `TRANSIENT_PROVIDER_FAILURE`, `TIMEOUT`, `RATE_LIMIT`, `UNKNOWN` (strict cap)  
- [ ] Non-retryable: `PERMANENT_PROVIDER_REJECTION`, `INVALID_RECIPIENT`, `AUTH_ERROR`  
- [ ] `domain.RetryPolicy`: max attempts **5**, exponential backoff + full jitter, cap 5 minutes (config-driven)  
- [ ] `RATE_LIMIT` honors `Retry-After` when present  
- [ ] Exhaustion → `FAILED_TERMINAL` + audit `RETRY_EXHAUSTED` + notification roll-up  
- [ ] Unit tests: classifier matrix; backoff monotonic under jitter bounds; no retry on invalid recipient  
- [ ] Stub strategy can simulate 500 then 200 → `RETRY_SCHEDULED` then `SUCCEEDED`  
### Depends on
- GF-08, GF-09  
---
## AR-02 — 24-hour idempotency & dedup retention
**As a** operator  
**I want** a defined retention window for idempotency/dedup keys  
**So that** storage stays bounded and producer guidance is clear  
### Acceptance criteria
- [ ] `expires_at = created_at + 24h` (configurable `notification.idempotency-ttl` / `dedup-ttl`)  
- [ ] Lookup ignores expired records (treat as miss → new notification allowed)  
- [ ] Documented in status/audit behavior and README/LLD  
- [ ] Optional scheduled purge job or lazy ignore (document which)  
- [ ] Test: expired key reuse creates new notification  
### Depends on
- BF-03, BF-04  
---
## AR-03 — Audit privacy & complete event set
**As a** security reviewer  
**I want** audit without unnecessary sensitive content  
**So that** FR 4.9 is satisfied  
### Acceptance criteria
- [ ] Full event set per LLD §10 / HLD §10 (including reject, duplicate, retry scheduled, terminal roll-ups)  
- [ ] Automated or review checklist: no tokens, webhook secrets, Authorization headers, raw PII message bodies in `payload_json`  
- [ ] Prefer templateKey + ids + reason codes  
### Depends on
- GF-10, AR-01, BF-04  
---
## AR-04 — Status roll-up & partial success
**As a** API consumer  
**I want** overall status to reflect mixed channel outcomes  
**So that** partial delivery is visible and defensible  
### Acceptance criteria
- [ ] Implement LLD §4.3 roll-up:  
  - any non-terminal delivery → `IN_PROGRESS`  
  - all succeeded → `COMPLETED`, `partialSuccess=false`  
  - mix success + terminal fail → `COMPLETED`, `partialSuccess=true`  
  - all terminal fail → `FAILED`  
  - expiry rules → `EXPIRED`  
- [ ] Integration tests for partial success and full failure  
### Depends on
- GF-07, AR-01  
---
## AR-05 — Configuration & operability surface
**As a** operator  
**I want** backoff, TTL, worker, and channel flags in config  
**So that** prototype behavior is tunable without code changes  
### Acceptance criteria
- [ ] `application.yml` keys match LLD §12  
- [ ] `NotificationProperties` bound and used by worker/retry/routing  
- [ ] README documents key properties for demo  
### Depends on
- NF-01, AR-01, AR-02  
---
## AR epic exit criteria
- [ ] Written “policy card” (or LLD sections) cited in demo: taxonomy table, backoff formula, 24h TTL  
- [ ] Tests prove non-retry of permanent failures and bounded retries for transient  
