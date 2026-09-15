# Cross-cutting / non-functional stories
Stories that span epics. Primary NF story lives in greenfield (`NF-01`). Additional items:
---
## NF-02 — Automated test harness
**As a** developer  
**I want** unit + `@SpringBootTest` / MockMvc coverage for critical paths  
**So that** brownfield refactors stay safe  
### Acceptance criteria
- [ ] Tests listed in LLD §14 matrix covered or explicitly deferred with reason  
- [ ] CI-friendly: `./gradlew test` green on H2  
### Depends on
- GF + BF core stories  
---
## NF-03 — Demo & documentation pack
**As a** reviewer  
**I want** curl examples and story→code map  
**So that** the assessment walkthrough is fast  
### Acceptance criteria
- [ ] README: run, POST sample, GET status, idempotent replay  
- [ ] Link HLD/LLD/stories  
- [ ] Package tree matches LLD §3  
### Depends on
- Epic exit criteria GF/BF/AR  
