# Notification Management Service

> **An AI-Assisted, Engineer-Led Reference Implementation**  
> A multi-channel notification ingestion and routing service built with Java 21, Spring Boot 3 (Spring JDBC), PostgreSQL, and H2.

---

## 📌 Executive Summary & Assessment Approach

This repository demonstrates a **production-oriented software engineering outcome** produced through **engineer-led execution accelerated by AI**. Rather than relying on autonomous orchestration, the architecture, domain boundaries, state models, and safety guarantees were designed by the lead engineer, while AI tools (GitHub Copilot, LLMs) were leveraged as force multipliers to scaffold boilerplate, generate raw SQL/DDL schemas, and draft test assertions.

### Engineering Methodology & Evolution
The codebase is structured to reflect three distinct operational engineering scenarios:
1. **Greenfield Phase:** Designing the core ingestion API, dynamic channel router, state tracking, and raw SQL persistence layer using Java 21 Records and `NamedParameterJdbcTemplate`.
2. **Brownfield Phase:** Safely refactoring channel logic into an extensible Strategy Pattern, introducing an idempotency/deduplication middleware layer, and adding a third channel adapter (Slack) without breaking existing interfaces.
3. **Ambiguous Requirement Phase:** Translating vague operational requirements (e.g., *"handle retries safely"*, *"prevent avoidable duplicates"*) into explicit SLAs, bounded exponential backoff policies, and 24-hour hash retention boundaries.

👉 **Read the full execution methodology:** [`docs/APPROACH.md`](docs/APPROACH.md)
## 🛠️ Technology Stack & Prerequisites

### Application & Runtime Stack
* **Language & SDK:** Java 21 (LTS - utilizing Java Records & Virtual Threads)
* **Framework:** Spring Boot 3.x (`spring-boot-starter-jdbc`, Spring Web, Spring Validation)
* **Persistence Layer:** Spring JDBC (`NamedParameterJdbcTemplate` with raw SQL execution)
* **Production Database:** PostgreSQL (Managed PCF Service Instance / AWS RDS)
* **Test Database:** H2 Database (In-Memory, `MODE=PostgreSQL`)
* **Build Tool:** Gradle (via `./gradlew` wrapper)
* **Deployment Platform:** Pivotal Cloud Foundry (PCF) / VMware Tanzu

### System Requirements
* **Cloud Foundry CLI (`cf` CLI)** installed and logged into target PCF foundation.
* **JDK 21 or higher** installed locally (for running via IDE or Gradle CLI).

---

## 🚀 Deployment & Local Execution

### Option 1: Deploy to Pivotal Cloud Foundry (PCF)
Deploy directly to PCF using the provided `manifest.yml`:

```bash
# 1. Build executable fat JAR with Gradle
./gradlew bootJar

# 2. Push application to PCF
cf push