package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.Courier;
import com.swiftdrop.delivery.courier.domain.CourierStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {
    boolean existsByIdentityUserId(UUID identityUserId);
    Optional<Courier> findByIdentityUserId(UUID identityUserId);
    Page<Courier> findAllByStatus(CourierStatus status, Pageable pageable);
    Page<Courier> findAllByCategory_Id(UUID categoryId, Pageable pageable);
    Page<Courier> findAllByStatusAndCategory_Id(CourierStatus status, UUID categoryId, Pageable pageable);
}
