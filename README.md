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
```

### Option 2: Run locally (prototype)

```bash
# Run tests
./gradlew test

# Start service
./gradlew bootRun
```

Service endpoints:

- `POST /api/v1/notifications`
- `GET /api/v1/notifications/{id}`

Quick demo (local)

1. Start the service locally (uses `application.yml` by default which enables the in-process worker):

```bash
./gradlew bootRun
```

2. Submit a notification (example):

```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
	"sourceSystem":"billing-service",
	"eventId":"evt-demo-1",
	"type":"PAYMENT_FAILED",
	"severity":"HIGH",
	"priority":"NORMAL",
	"recipients":[{"recipientId":"user-1","email":"user1@example.com"}],
	"requestedChannels":["EMAIL"]
  }' | jq
```

The response will be `202 Accepted` with a JSON body containing `notificationId`.

3. Poll the status until delivery completes:

```bash
NOTIF_ID=<notificationId-from-post>
curl -s http://localhost:8080/api/v1/notifications/${NOTIF_ID} | jq
```

4. (Optional) Fetch the audit trail:

```bash
curl -s http://localhost:8080/api/v1/notifications/${NOTIF_ID}/audit | jq
```

Notes:
- When running tests the `acceptance` profile uses an in-memory H2 database and disables the scheduled worker for deterministic tests. For live local demo enable the worker (`notification.worker.enabled=true`) in `application.yml` or run the demo without the `acceptance` profile.

Local E2E demo script

If you want a quick, repeatable local end-to-end demo (build, start app with stubbed providers, submit a notification, trigger processing, and tail logs) use the included script:

```bash
./scripts/run-local-e2e.sh
```

Expected outcome: the script builds the fat JAR, starts the application with profiles `acceptance,local-e2e` (which registers local stub strategies), posts the sample payload `docs/samples/payload-e2e.json`, triggers `/internal/process-now`, polls the audit endpoint and prints `DELIVERY_SUCCEEDED` when observed. Logs are saved to `/tmp/notification_local_e2e.log` by the script.

Testcontainers / Docker note

Some integration tests in this repository use Testcontainers against PostgreSQL (see `docs/testcases.md`). Testcontainers requires a working Docker daemon. If Docker is not available on your machine or CI runner you will see errors such as:

```
Could not find a valid Docker environment. Please check configuration. Attempted configurations were:
	UnixSocketClientProviderStrategy: failed with exception InvalidConfigurationException (Could not find unix domain socket). Root cause NoSuchFileException (/var/run/docker.sock)
java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
```

Recommended actions:
- For local runs install and start Docker so Testcontainers can spin up Postgres.
- For CI, run on a runner that provides Docker or configure Testcontainers to use a remote Docker endpoint.
- Use H2-based acceptance tests for quick feedback if Docker is unavailable.


Reference design docs:

- `docs/architecture.md`
- `docs/lld.md`
- `docs/stories/01-greenfield.md`
