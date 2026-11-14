package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingReceiptRepository extends JpaRepository<BookingReceipt, Long> {

    Optional<BookingReceipt> findByParcelId(UUID parcelId);

    Optional<BookingReceipt> findByReceiptNumber(String receiptNumber);
}

