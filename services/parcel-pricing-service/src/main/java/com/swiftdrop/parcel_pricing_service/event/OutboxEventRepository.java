package com.swiftdrop.parcel_pricing_service.event;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Pending events, oldest first. SKIP LOCKED (lock timeout -2) lets two running copies of the
     * service share the outbox without publishing the same row twice at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null and e.nextAttemptAt <= :now order by e.createdAt")
    List<OutboxEvent> lockPending(@Param("now") OffsetDateTime now, Pageable page);
}
