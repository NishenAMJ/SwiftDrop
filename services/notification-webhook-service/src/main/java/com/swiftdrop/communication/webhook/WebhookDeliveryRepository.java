package com.swiftdrop.communication.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Page<WebhookDelivery> findBySubscriptionId(UUID subscriptionId, Pageable pageable);

    Page<WebhookDelivery> findBySubscriptionIdAndStatus(UUID subscriptionId, WebhookDeliveryStatus status, Pageable pageable);

    Page<WebhookDelivery> findByStatus(WebhookDeliveryStatus status, Pageable pageable);

    boolean existsBySubscriptionIdAndEventId(UUID subscriptionId, UUID eventId);

    @Query(value = """
            SELECT * FROM notification_webhook.webhook_deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> findDuePendingDeliveriesForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
