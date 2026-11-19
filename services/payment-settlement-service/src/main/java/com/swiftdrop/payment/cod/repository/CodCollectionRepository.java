package com.swiftdrop.payment.cod.repository;

import com.swiftdrop.payment.cod.entity.CodCollection;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CodCollectionRepository extends JpaRepository<CodCollection, UUID> {
    Optional<CodCollection> findBySourceEventId(UUID sourceEventId);
    Optional<CodCollection> findByBookingId(String bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodCollection c where c.id in :ids")
    List<CodCollection> lockAllById(@Param("ids") Collection<UUID> ids);
}
