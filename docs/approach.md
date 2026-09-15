# Engineering Methodology & Execution Approach

> **Project:** Notification Management Service  
> **Philosophy:** Engineer-Led Architecture, AI-Accelerated Implementation

---

## 🎯 Primary Objective

The goal of this project is to build a working prototype that transforms a notification-management requirement into a
reviewable, production-oriented engineering outcome using AI-assisted software
engineering.

Rather than allowing an AI agent to operate autonomously, the lead engineer drives all architectural boundaries, domain contracts, error taxonomies, and safety guards—using AI exclusively as a force multiplier for boilerplate generation, test scaffolding, and code refactoring.

---

## 🔄 Four-Phase Execution Strategy

### Phase 1: Architecture & Domain Modeling (Design-First)
1. **Define Core System Contracts:** Start with High-Level Architecture (`docs/ARCHITECTURE.md`), which outline ingestion boundaries, database model and asynchronous queues.
2. **Specify Low-Level Design:** Based on the above HLD/Architecture document, start Document database schemas, state transition matrices, design patterns (Strategy, Factory) in `docs/LLD.md`.
3. **Task Decomposition:** After going through HLD/LLD document, i started breaking the requirement into actionable, reviewable epics and stories across three operational scenarios (`docs/stories/`).

### Phase 2: Greenfield Development (Core Ingestion Engine)
* **Goal:** Build the foundational MVP from scratch using AI assistance for speed and accuracy.
* **Execution:**
    * Implement `POST /api/v1/notifications` REST endpoint with strict payload validation.
    * Establish database persistence (PostgreSQL for runtime, H2 for tests) and state tracking (`ACCEPTED`, `QUEUED`, `DELIVERED`, `FAILED`).
    * Implement the core Notification Router evaluating severity overrides and recipient channel preferences.

### Phase 3: Brownfield Expansion & Refactoring (Evolutionary Engineering)
* **Goal:** Modify the existing service safely, simulating a real-world enterprise codebase evolution.
* **Execution:**
    * Refactor channel delivery logic into an extensible **Strategy Pattern** adapter interface.
    * Integrate a new delivery channel adapter (e.g., Slack Webhook) alongside Email and SMS.
    * Implement an **Idempotency & Deduplication Gateway** checking submission hashes and `Idempotency-Key` headers without breaking existing contracts.

### Phase 4: Resolving Ambiguous Operational Requirements & Validation
* **Goal:** Transform non-functional or ambiguous expectations into concrete engineering implementations.
* **Execution:**
    * Implement bounded **Exponential Backoff Retries with Jitter** distinguishing transient network timeouts from permanent recipient rejections.
    * Define explicit deduplication retention boundaries (24-hour window) and audit log privacy rules (scrubbing sensitive payload content).
    * Build end-to-end JUnit 5 integration test suites to validate edge cases and state transitions.

---

## 🛠️ AI-Assisted Engineering Protocol

To guarantee safety, correctness, and code quality, all AI-generated code follows a strict **3-Step Human Verification Loop**: