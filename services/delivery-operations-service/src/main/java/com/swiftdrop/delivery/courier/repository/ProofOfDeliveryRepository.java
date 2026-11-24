package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.ProofOfDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProofOfDeliveryRepository extends JpaRepository<ProofOfDelivery, UUID> {
    Optional<ProofOfDelivery> findByAssignment_Id(UUID assignmentId);
}
