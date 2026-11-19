package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.MerchantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, Long> {

    /** Only ever query orders through the owning merchant. */
    Page<MerchantOrder> findByMerchantId(Long merchantId, Pageable pageable);

    boolean existsByMerchantIdAndMerchantReference(Long merchantId, String merchantReference);

    /** Next value of the order-number sequence (see V3 migration). */
    @Query(value = "SELECT nextval('merchant_order_number_seq')", nativeQuery = true)
    Long nextOrderSequence();
}
