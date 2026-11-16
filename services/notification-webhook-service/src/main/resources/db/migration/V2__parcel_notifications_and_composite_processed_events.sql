ALTER TABLE notification_webhook.processed_events
    ADD COLUMN consumer VARCHAR(50) NOT NULL DEFAULT 'identity-notifications';

ALTER TABLE notification_webhook.processed_events
    DROP CONSTRAINT processed_events_pkey;

ALTER TABLE notification_webhook.processed_events
    ADD CONSTRAINT processed_events_pkey PRIMARY KEY (event_id, consumer);

ALTER TABLE notification_webhook.notifications
    DROP CONSTRAINT IF EXISTS chk_notifications_channel;

ALTER TABLE notification_webhook.notifications
    ADD CONSTRAINT chk_notifications_channel CHECK (channel IN ('EMAIL', 'SMS'));

ALTER TABLE notification_webhook.notifications
    ADD COLUMN parcel_id UUID;

CREATE INDEX idx_notifications_parcel_id ON notification_webhook.notifications (parcel_id);
