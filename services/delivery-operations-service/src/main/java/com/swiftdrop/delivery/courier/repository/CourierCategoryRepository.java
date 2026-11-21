package com.swiftdrop.delivery.courier.repository;

import com.swiftdrop.delivery.courier.domain.CourierCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourierCategoryRepository extends JpaRepository<CourierCategory, UUID> {
    boolean existsByCodeIgnoreCase(String code);
}
