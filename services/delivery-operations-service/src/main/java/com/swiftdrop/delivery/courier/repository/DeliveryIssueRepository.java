package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.DeliveryIssue;
import com.swiftdrop.delivery.courier.domain.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeliveryIssueRepository extends JpaRepository<DeliveryIssue, UUID> {
    Page<DeliveryIssue> findAllByStatus(IssueStatus status, Pageable pageable);
    Page<DeliveryIssue> findAllByCourier_Id(UUID courierId, Pageable pageable);
    Page<DeliveryIssue> findAllByCourier_IdAndStatus(UUID courierId, IssueStatus status, Pageable pageable);
}
