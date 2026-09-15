#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG=/tmp/notification_local_e2e.log
PIDFILE=/tmp/notification_pid
JAR="$ROOT/build/libs/notification-management-system-0.0.1-SNAPSHOT.jar"

echo "Working directory: $ROOT"

cd "$ROOT"

echo "Building application jar..."
./gradlew bootJar -x test

echo "Starting application (acceptance,local-e2e) with worker enabled..."
nohup java -Dspring.profiles.active=acceptance,local-e2e \
  -Dnotification.worker.enabled=true \
  -Dlogging.level.com.interview.assessment.notification=DEBUG \
  -jar "$JAR" >"$LOG" 2>&1 &
echo $! > "$PIDFILE"
echo "Started (pid $(cat $PIDFILE)), logs -> $LOG"

echo "Waiting for /actuator/health UP (60s timeout)..."
for i in $(seq 1 60); do
  if curl -sS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Application healthy"
    break
  fi
  echo "waiting... $i"
  sleep 1
done

if ! curl -sS http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
  echo "Application did not become healthy, see $LOG"
  exit 1
fi

echo "Submitting sample notification..."
curl -sS -X POST http://localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -d @"$ROOT/docs/samples/payload-e2e.json" \
  | tee /tmp/e2e_post_resp.json

if command -v jq >/dev/null 2>&1; then
  jq . /tmp/e2e_post_resp.json || true
else
  cat /tmp/e2e_post_resp.json
fi

NOTIF_ID=$(jq -r .notificationId /tmp/e2e_post_resp.json)
echo "Notification ID: $NOTIF_ID"

echo "Triggering worker (POST /internal/process-now)"
curl -sS -X POST http://localhost:8080/internal/process-now -o /tmp/process_resp.txt || true
cat /tmp/process_resp.txt || true

echo "Polling audit events for DELIVERY_SUCCEEDED (30s)..."
for i in $(seq 1 30); do
  echo "poll $i"
  curl -sS http://localhost:8080/api/v1/notifications/$NOTIF_ID/audit -o /tmp/audit_resp.json || true
  if command -v jq >/dev/null 2>&1; then
    if jq -r '.[].eventType' /tmp/audit_resp.json 2>/dev/null | grep -q DELIVERY_SUCCEEDED; then
      echo "DELIVERY_SUCCEEDED observed"
      jq . /tmp/audit_resp.json || true
      break
    fi
  else
    if grep -q DELIVERY_SUCCEEDED /tmp/audit_resp.json 2>/dev/null; then
      echo "DELIVERY_SUCCEEDED observed"
      cat /tmp/audit_resp.json
      break
    fi
  fi
  sleep 1
done

echo "Final notification status:"
curl -sS http://localhost:8080/api/v1/notifications/$NOTIF_ID | jq . || curl -sS http://localhost:8080/api/v1/notifications/$NOTIF_ID

echo "Stopping application (pid $(cat $PIDFILE))"
kill "$(cat $PIDFILE)" || true
rm -f "$PIDFILE"

echo "Tail of logs ($LOG):"
tail -n 200 "$LOG" || true

echo "Done."

