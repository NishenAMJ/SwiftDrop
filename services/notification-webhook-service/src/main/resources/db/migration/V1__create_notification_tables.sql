CREATE TABLE notification_webhook.contacts (
    user_id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    last_event_occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_webhook.processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_webhook.notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_contacts_email ON notification_webhook.contacts (email);
CREATE INDEX idx_notifications_recipient ON notification_webhook.notifications (recipient_user_id);
