package com.swiftdrop.tracking.tracking;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingRecordRepository extends JpaRepository<TrackingRecord, UUID> {
}
