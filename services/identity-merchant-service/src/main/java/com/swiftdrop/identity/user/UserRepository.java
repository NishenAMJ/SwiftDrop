package com.swiftdrop.identity.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Emails are stored lower-case; pass a normalized email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRoles_Name(RoleName name);
}
