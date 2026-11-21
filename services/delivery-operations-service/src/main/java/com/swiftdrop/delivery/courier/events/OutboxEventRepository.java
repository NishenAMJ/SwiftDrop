package com.swiftdrop.delivery.courier.events;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findAllByOrderByCreatedAtAsc();
}
