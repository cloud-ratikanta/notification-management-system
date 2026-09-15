# PR1 (BF-01) — Skeleton

Purpose: create a lightweight, reviewable skeleton PR that outlines the refactor of channel delivery into strategy classes and a registry. This is intentionally non-invasive: no production logic changes in this skeleton, only design, interfaces, and TODOs for the implementation PR.

Scope (BF-01 — Refactor channel logic behind Strategy)
- Make `DeliveryOrchestrator` depend on a `ChannelStrategyRegistry` instead of branching by `Channel`.
- Ensure Email/SMS provider I/O lives in strategy classes.
- Registry should fail-closed for unknown channels.
- Preserve existing GF behavior for now (no external API changes).

What this skeleton contains
- Design and acceptance criteria summary (this file)
- Public interface drafts (names and responsibilities)
- File-level TODO list mapping to concrete implementation tasks
- Tests to add (list of unit/integration tests to author in implementation PR)

Suggested public interfaces (no code in this PR; add to implementation PR)
- ChannelDeliveryStrategy: deliver(DeliveryCommand) -> DeliveryResult
- ChannelStrategyRegistry: get(Channel) -> ChannelDeliveryStrategy | throw

Planned files to create or modify in implementation PR
- src/main/java/com/interview/assessment/notification/strategy/ChannelDeliveryStrategy.java (interface)
- src/main/java/com/interview/assessment/notification/strategy/ChannelStrategyRegistry.java
- src/main/java/com/interview/assessment/notification/strategy/EmailDeliveryStrategy.java (move Email logic)
- src/main/java/com/interview/assessment/notification/strategy/SmsDeliveryStrategy.java (move SMS logic)
- src/main/java/com/interview/assessment/notification/strategy/SlackDeliveryStrategy.java (BF-02)
- src/main/java/com/interview/assessment/notification/orchestrator/DeliveryOrchestrator.java (depend on registry only)
- Tests: DeliveryOrchestratorTest, ChannelStrategyRegistryTest, Email/Sms strategy unit tests

Open questions for reviewers (to be answered during skeleton review)
- Registry lifecycle: inject via Spring @Component or build programmatically? (prefer @Component + auto-discovery)
- How to configure per-channel enablement flags? Keep existing properties (`notification.channels.*`) — no change required in API.

Next steps (implementation PR)
1. Add interfaces and registry implementation.
2. Extract Email/SMS logic into strategy classes and adapt DI configuration.
3. Add unit tests to cover strategies and orchestrator behavior-preserving assertions.
4. Add CI run and run full test suite.

Mark this PR as Draft while design and high-level decisions are discussed.

