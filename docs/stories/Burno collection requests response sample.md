# Bruno collection: request/response samples

Below are example POST requests to `POST /api/v1/notifications` and the expected responses. Replace host/port and IDs as appropriate for your environment.

1) Single recipient, requested EMAIL (minimal happy-path)

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"billing-service",
    "eventId":"evt-1",
    "type":"PAYMENT_FAILED",
    "severity":"HIGH",
    "priority":"NORMAL",
    "recipients":[{"recipientId":"user-1","email":"user1@example.com"}],
    "requestedChannels":["EMAIL"]
  }' | jq
```

Expected response (HTTP 202 Accepted):
```json
{
  "notificationId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["EMAIL"],
  "createdAt": "2026-09-15T14:00:00Z"
}
```

2) Submit with an Idempotency-Key (first time -> accepted)

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: key-123" \
  -d '{
    "sourceSystem":"ops-service",
    "eventId":"evt-2",
    "type":"DISK_ALERT",
    "severity":"HIGH",
    "priority":"NORMAL",
    "recipients":[{"recipientId":"user-2","email":"user2@example.com"}],
    "requestedChannels":["EMAIL"]
  }' | jq
```

Expected response (HTTP 202 Accepted):
```json
{
  "notificationId": "1a2b3c4d-....",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["EMAIL"],
  "createdAt": "2026-09-15T14:05:00Z"
}
```

3) Replay with same Idempotency-Key + identical payload → replay (duplicate)

Request: repeat the previous request with the same `Idempotency-Key` and identical body.

Expected response (HTTP 200 OK):
```json
{
  "notificationId": "1a2b3c4d-....",
  "status": "QUEUED",
  "duplicate": true,
  "selectedChannels": ["EMAIL"],
  "createdAt": "2026-09-15T14:05:00Z"
}
```

4) Idempotency conflict (same Idempotency-Key but different payload)

Sequence:
- First POST with `Idempotency-Key: conflict-key` and payload A → 202
- Second POST with `Idempotency-Key: conflict-key` and different payload B → 409

Expected conflict response (HTTP 409 Conflict):
```json
{
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "Idempotency conflict - different request for same key",
  "correlationId": null,
  "details": []
}
```

5) Scheduled delivery (future `scheduleAt`)

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"scheduler",
    "eventId":"evt-sched",
    "type":"REMINDER",
    "severity":"LOW",
    "priority":"LOW",
    "recipients":[{"recipientId":"user-s","email":"s@example.com"}],
    "requestedChannels":["EMAIL"],
    "scheduleAt":"2026-09-15T20:00:00Z"
  }' | jq
```

Expected response (HTTP 202):
```json
{
  "notificationId": "abcde-....",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["EMAIL"],
  "createdAt": "2026-09-15T14:10:00Z"
}
```

6) Multiple recipients and multiple channels (CRITICAL routing)

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"alerts",
    "eventId":"evt-multi",
    "type":"OUTAGE",
    "severity":"CRITICAL",
    "priority":"HIGH",
    "recipients":[
      {"recipientId":"u1","email":"u1@example.com","phone":"+10000000001"},
      {"recipientId":"u2","email":"u2@example.com"}
    ]
  }' | jq
```

Expected response (HTTP 202):
```json
{
  "notificationId": "multi-....",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["SMS","SLACK","EMAIL"],
  "createdAt": "2026-09-15T14:12:00Z"
}
```

7) Validation error (missing required field / empty recipients) → 400 VALIDATION_FAILED

Request (recipients empty):
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"billing-service",
    "eventId":"evt-bad",
    "type":"PAYMENT_FAILED",
    "severity":"HIGH",
    "priority":"NORMAL",
    "recipients":[]
  }' | jq
```

Expected response (HTTP 400 Bad Request):
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "correlationId": null,
  "details": [
    { "field": "recipients", "reason": "must not be empty" }
  ]
}
```

8) Business validation error (`expiresAt` in the past) → 400 BAD_REQUEST

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"billing-service",
    "eventId":"evt-expired",
    "type":"PAYMENT_FAILED",
    "severity":"HIGH",
    "priority":"NORMAL",
    "recipients":[{"recipientId":"user-1","email":"user1@example.com"}],
    "expiresAt":"2020-01-01T00:00:00Z"
  }' | jq
```

Expected response (HTTP 400 Bad Request):
```json
{
  "code": "BAD_REQUEST",
  "message": "expiresAt must be in the future",
  "correlationId": null,
  "details": []
}
```

9) Correlation-Id header echoed in error responses

Request (invalid payload) with header:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Correlation-Id: corr-789" \
  -d '{}' | jq
```

Expected response (HTTP 400):
```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "correlationId": "corr-789",
  "details": [
    {"field":"sourceSystem","reason":"must not be blank"},
    {"field":"eventId","reason":"must not be blank"},
    {"field":"type","reason":"must not be blank"},
    {"field":"severity","reason":"must not be null"},
    {"field":"priority","reason":"must not be null"},
    {"field":"recipients","reason":"must not be empty"}
  ]
}
```

10) Missing recipient address: accepted now, worker records INVALID_RECIPIENT later

Request:
```bash
curl -s -X POST http://localhost:8080/api/v1/notifications \
  -H "Content-Type: application/json" \
  -d '{
    "sourceSystem":"alerts",
    "eventId":"evt-noaddr",
    "type":"NOTE",
    "severity":"HIGH",
    "priority":"NORMAL",
    "recipients":[{"recipientId":"user-noaddr"}],
    "requestedChannels":["EMAIL"]
  }' | jq
```

Immediate response (HTTP 202):
```json
{
  "notificationId": "noaddr-....",
  "status": "QUEUED",
  "duplicate": false,
  "selectedChannels": ["EMAIL"],
  "createdAt": "2026-09-15T14:20:00Z"
}
```

Later (after worker runs) the delivery row will be updated to `FAILED_TERMINAL` and a delivery_attempt will record `INVALID_RECIPIENT`.

---

Tips:
- Use `jq` to pretty-print responses in your terminal.
- For deterministic test runs the `acceptance` profile uses H2 and disables the scheduled worker; to exercise the worker locally run without that profile or enable `notification.worker.enabled=true` in `application.yml`.

