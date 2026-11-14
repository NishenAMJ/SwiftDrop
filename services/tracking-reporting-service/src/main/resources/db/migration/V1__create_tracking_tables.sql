CREATE TABLE tracking_reporting.tracking_records (
    parcel_id                  UUID         PRIMARY KEY,
    tracking_number            VARCHAR(32)  NOT NULL,
    current_status             VARCHAR(30)  NOT NULL,
    latest_safe_location_label VARCHAR(120),
    last_updated_at            TIMESTAMPTZ  NOT NULL,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_tracking_records_tracking_number UNIQUE (tracking_number),
    CONSTRAINT chk_tracking_records_tracking_number_not_blank
        CHECK (btrim(tracking_number) <> ''),
    CONSTRAINT chk_tracking_records_current_status_not_blank
        CHECK (btrim(current_status) <> ''),
    CONSTRAINT chk_tracking_records_safe_location_not_blank
        CHECK (latest_safe_location_label IS NULL OR btrim(latest_safe_location_label) <> '')
);

CREATE TABLE tracking_reporting.tracking_history (
    history_id          UUID         PRIMARY KEY,
    parcel_id           UUID         NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    public_description  VARCHAR(200) NOT NULL,
    safe_location_label VARCHAR(120),
    event_time           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_tracking_history_record
        FOREIGN KEY (parcel_id)
        REFERENCES tracking_reporting.tracking_records (parcel_id),
    CONSTRAINT chk_tracking_history_event_type_not_blank
        CHECK (btrim(event_type) <> ''),
    CONSTRAINT chk_tracking_history_status_not_blank
        CHECK (btrim(status) <> ''),
    CONSTRAINT chk_tracking_history_description_not_blank
        CHECK (btrim(public_description) <> ''),
    CONSTRAINT chk_tracking_history_safe_location_not_blank
        CHECK (safe_location_label IS NULL OR btrim(safe_location_label) <> '')
);

CREATE TABLE tracking_reporting.processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_processed_events_event_type_not_blank
        CHECK (btrim(event_type) <> '')
);

CREATE INDEX idx_tracking_records_status_updated
    ON tracking_reporting.tracking_records (current_status, last_updated_at DESC);

CREATE INDEX idx_tracking_history_parcel_timeline
    ON tracking_reporting.tracking_history (parcel_id, event_time, history_id);

CREATE INDEX idx_processed_events_processed_at
    ON tracking_reporting.processed_events (processed_at);
