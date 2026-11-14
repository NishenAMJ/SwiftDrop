package com.swiftdrop.tracking.tracking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingHistoryRepository extends JpaRepository<TrackingHistory, UUID> {

    long countByParcelId(UUID parcelId);

    Optional<TrackingHistory> findFirstByParcelIdOrderByEventTimeDescHistoryIdDesc(UUID parcelId);

    List<TrackingHistory> findByParcelIdOrderByEventTimeAscHistoryIdAsc(UUID parcelId);
}
