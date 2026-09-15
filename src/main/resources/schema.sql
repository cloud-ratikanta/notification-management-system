CREATE TABLE IF NOT EXISTS notification (
  id UUID PRIMARY KEY,
  source_system VARCHAR(64) NOT NULL,
  event_id VARCHAR(128) NOT NULL,
  correlation_id VARCHAR(128) NOT NULL,
  type VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  selected_channels VARCHAR(256),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_recipient (
  id UUID PRIMARY KEY,
  notification_id UUID NOT NULL,
  recipient_ref VARCHAR(128) NOT NULL,
  email VARCHAR(256),
  phone VARCHAR(32),
  slack_target VARCHAR(128),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_recipient_notification FOREIGN KEY (notification_id) REFERENCES notification(id)
);

CREATE TABLE IF NOT EXISTS delivery (
  id UUID PRIMARY KEY,
  notification_id UUID NOT NULL,
  recipient_id VARCHAR(128) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  next_attempt_at TIMESTAMP WITH TIME ZONE,
  last_error_class VARCHAR(64),
  last_attempt_at TIMESTAMP WITH TIME ZONE,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT fk_delivery_notification FOREIGN KEY (notification_id) REFERENCES notification(id)
);

CREATE TABLE IF NOT EXISTS delivery_attempt (
  id UUID PRIMARY KEY,
  delivery_id UUID NOT NULL,
  attempt_no INT NOT NULL,
  started_at TIMESTAMP WITH TIME ZONE NOT NULL,
  finished_at TIMESTAMP WITH TIME ZONE,
  outcome VARCHAR(32) NOT NULL,
  error_class VARCHAR(64),
  provider_ref VARCHAR(128),
  safe_detail VARCHAR(512),
  CONSTRAINT fk_attempt_delivery FOREIGN KEY (delivery_id) REFERENCES delivery(id),
  CONSTRAINT uq_attempt_no UNIQUE (delivery_id, attempt_no)
);

CREATE TABLE IF NOT EXISTS idempotency_record (
  source_system VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  notification_id UUID NOT NULL,
  request_hash CHAR(64) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (source_system, idempotency_key)
);

CREATE TABLE IF NOT EXISTS audit_event (
  id UUID PRIMARY KEY,
  notification_id UUID,
  delivery_id UUID,
  event_type VARCHAR(64) NOT NULL,
  payload_json VARCHAR(1024) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_created_at ON notification(created_at);
CREATE INDEX IF NOT EXISTS idx_delivery_notification_id ON delivery(notification_id);
CREATE INDEX IF NOT EXISTS idx_audit_notification_id ON audit_event(notification_id, created_at);

