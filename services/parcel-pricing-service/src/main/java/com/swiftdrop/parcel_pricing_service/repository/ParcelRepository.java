package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<Parcel> findByIdAndCustomerId(UUID id, UUID customerId);
}

