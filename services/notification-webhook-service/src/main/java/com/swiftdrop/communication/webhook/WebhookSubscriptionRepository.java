package com.swiftdrop.communication.webhook;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, UUID> {

    Page<WebhookSubscription> findByMerchantId(UUID merchantId, Pageable pageable);

    Optional<WebhookSubscription> findBySubscriptionIdAndMerchantId(UUID subscriptionId, UUID merchantId);

    List<WebhookSubscription> findByMerchantIdAndActiveTrue(UUID merchantId);

    List<WebhookSubscription> findByMerchantId(UUID merchantId);
}
