package com.swiftdrop.tracking.tracking;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingRecordRepository extends JpaRepository<TrackingRecord, UUID> {

    Optional<TrackingRecord> findByTrackingNumber(String trackingNumber);
}
