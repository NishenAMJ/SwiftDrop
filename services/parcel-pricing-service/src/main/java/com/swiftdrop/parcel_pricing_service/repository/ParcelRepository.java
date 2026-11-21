package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ParcelRepository extends JpaRepository<Parcel, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.id = :id")
    Optional<Parcel> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcel p where p.trackingNumber = :trackingNumber")
    Optional<Parcel> lockByTrackingNumber(@Param("trackingNumber") String trackingNumber);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    List<Parcel> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    Optional<Parcel> findByIdAndCustomerId(UUID id, UUID customerId);

    /** Unpaid checkouts whose price validity has passed. They stay in the table for payment audit. */
    @Modifying
    @Query("""
            update Parcel p set p.status = 'EXPIRED', p.updatedAt = :now
            where p.status = 'CHECKOUT_DRAFT' and p.pricingExpiresAt < :now
            """)
    int expireDrafts(@Param("now") OffsetDateTime now);
}
