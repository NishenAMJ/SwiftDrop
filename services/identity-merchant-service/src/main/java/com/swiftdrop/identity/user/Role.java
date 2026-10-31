package com.swiftdrop.identity.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/** Read-only lookup of the rows created by V1__create_roles.sql. */
@Entity
@Immutable
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "role_id")
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, length = 30)
    private RoleName name;

    protected Role() {
    }

    public Short getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }
}
