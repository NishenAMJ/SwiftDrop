CREATE TABLE notification_webhook.webhook_subscriptions (
    subscription_id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    url VARCHAR(2048) NOT NULL,
    event_types VARCHAR(500) NOT NULL,
    secret_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    paused_reason VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_webhook_subscriptions_merchant_id ON notification_webhook.webhook_subscriptions (merchant_id);

CREATE TABLE notification_webhook.webhook_deliveries (
    delivery_id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES notification_webhook.webhook_subscriptions(subscription_id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    last_status_code INT,
    last_error VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_deliveries_subscription_event UNIQUE (subscription_id, event_id)
);

CREATE INDEX idx_webhook_deliveries_status_next_attempt ON notification_webhook.webhook_deliveries (status, next_attempt_at);
