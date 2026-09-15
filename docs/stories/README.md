# Stories — Notification Management Service
> **Parent docs:** [`architecture.md`](../architecture.md) (HLD) · [`lld.md`](../lld.md) (LLD) · [`approach.md`](../approach.md)  
> **Base package:** `com.interview.assessment.notification`  
> **Stack:** Java 21 · Spring Boot 3 · Spring JDBC · PostgreSQL / H2
Stories are grouped by the three assessment scenarios. Implement in order: **Greenfield → Brownfield → Ambiguous hardening**.
| File | Scenario | Focus |
|------|----------|--------|
| [`01-greenfield.md`](01-greenfield.md) | 3.1 Core vertical slice | Submit, route, async deliver, status, audit basics |
| [`02-brownfield.md`](02-brownfield.md) | 3.2 Multi-layer change | Strategy refactor, Slack, idempotency/dedup |
| [`03-ambiguous.md`](03-ambiguous.md) | 3.3 Explicit policies | Retry taxonomy/backoff, 24h retention, privacy audit |
| [`00-nonfunctional.md`](00-nonfunctional.md) | Cross-cutting | Test harness, demo pack |
## Story ID convention
| Prefix | Meaning |
|--------|---------|
| `GF-##` | Greenfield |
| `BF-##` | Brownfield |
| `AR-##` | Ambiguous requirement → explicit design |
| `NF-##` | Non-functional / cross-cutting (infra, test harness) |
## Suggested delivery order (2–3 days)
```text
Day 1  NF-01, GF-01..GF-07   scaffold + submit/route/queue/status + Email/SMS stubs
Day 2  GF-08..GF-10, BF-01..BF-04   worker/retry skeleton + Strategy + Slack + idempotency
Day 3  AR-01..AR-04, polish tests/docs   failure taxonomy, TTL, audit privacy, demo
```
## Traceability (FR → stories)
| FR | Primary stories |
|----|-----------------|
| 4.1 Submit | GF-03, GF-04 |
| 4.2 Status | GF-07 |
| 4.3 Routing | GF-05 |
| 4.4 Dedup / idempotency | BF-03, BF-04, AR-02 |
| 4.5 Retry / failures | GF-09, AR-01 |
| 4.9 Audit | GF-10, AR-03 |
| Async processing | GF-06, GF-08 |
| Brownfield Strategy + Slack | BF-01, BF-02 |
## Definition of Done (all stories)
- [ ] Code under `com.interview.assessment.notification.*` packages per LLD §3  
- [ ] Unit and/or integration test where listed in acceptance criteria  
- [ ] No secrets or full message bodies in audit/logs  
- [ ] Behavior matches LLD state model and HTTP contracts unless deviation is documented  
---
*Stories are engineer-led scope for the prototype; not a full product backlog.*
