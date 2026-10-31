package com.swiftdrop.identity.courier;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourierRepository extends JpaRepository<Courier, UUID>, JpaSpecificationExecutor<Courier> {
}
