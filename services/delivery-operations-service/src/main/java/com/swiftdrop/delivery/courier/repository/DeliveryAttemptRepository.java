package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    long countByParcelReferenceIgnoreCase(String parcelReference);
    List<DeliveryAttempt> findAllByAssignment_IdOrderByAttemptNumberAsc(UUID assignmentId);
}
