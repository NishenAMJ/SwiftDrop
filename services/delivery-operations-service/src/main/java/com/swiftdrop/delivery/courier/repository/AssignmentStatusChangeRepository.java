package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.AssignmentStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssignmentStatusChangeRepository extends JpaRepository<AssignmentStatusChange, UUID> {
    List<AssignmentStatusChange> findAllByAssignment_IdOrderByChangedAtAscIdAsc(UUID assignmentId);
}
