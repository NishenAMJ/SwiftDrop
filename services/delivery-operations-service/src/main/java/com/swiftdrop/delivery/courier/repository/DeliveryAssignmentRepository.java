package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
    boolean existsByParcelReferenceIgnoreCaseAndStatusIn(String parcelReference, Collection<AssignmentStatus> statuses);
    Page<DeliveryAssignment> findAllByParcelReferenceIgnoreCase(String parcelReference, Pageable pageable);
    Page<DeliveryAssignment> findAllByCourier_IdAndParcelReferenceIgnoreCase(UUID courierId, String parcelReference,
                                                                            Pageable pageable);
    Page<DeliveryAssignment> findAllByStatus(AssignmentStatus status, Pageable pageable);
    Page<DeliveryAssignment> findAllByCourier_Id(UUID courierId, Pageable pageable);
    Page<DeliveryAssignment> findAllByCourier_IdAndStatus(UUID courierId, AssignmentStatus status, Pageable pageable);
}
