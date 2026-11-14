package com.swiftdrop.tracking.tracking;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingHistoryRepository extends JpaRepository<TrackingHistory, UUID> {

    long countByParcelId(UUID parcelId);
}
