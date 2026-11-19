CREATE INDEX idx_tracking_history_event_type_time
    ON tracking_reporting.tracking_history (event_type, event_time, parcel_id);
