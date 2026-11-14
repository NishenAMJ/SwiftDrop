package com.swiftdrop.parcel_pricing_service.repository;

import com.swiftdrop.parcel_pricing_service.entity.DeliveryZone;
import com.swiftdrop.parcel_pricing_service.entity.ZoneStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<DeliveryZone, Long> {

    List<DeliveryZone> findByStatus(ZoneStatus status, Sort sort);

    Optional<DeliveryZone> findByCode(String code);

    Optional<DeliveryZone> findByCodeIgnoreCase(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
