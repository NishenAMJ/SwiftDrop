package com.swiftdrop.identity.user;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "user_id")
    private UUID id;

    /** Always lower-case (also enforced by a database check). */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** Null for accounts that only use Google login. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** True for a new dispatcher until they replace the initial password. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** Google's stable user ID ("sub"), once the account has used Google login. */
    @Column(name = "google_subject", length = 255)
    private String googleSubject;

    /** Roles that are active right now. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @BatchSize(size = 50)
    private Set<Role> roles = new HashSet<>();

    /**
     * The user's merchant, in any status. Read-only: computed by the database when the user is
     * loaded, so it is current for every new request.
     */
    @Formula("(select m.merchant_id from identity_merchant.merchants m where m.user_id = user_id)")
    private UUID merchantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected User() {
    }

    User(String email, String fullName, String phoneNumber, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void setPasswordHash(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    /** Remembers the Google account that may log in to this user. */
    void linkGoogle(String googleSubject, Instant now) {
        this.googleSubject = googleSubject;
        this.updatedAt = now;
    }

    void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    void updateProfile(String fullName, String phoneNumber, Instant now) {
        if (fullName != null) {
            this.fullName = fullName;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        this.updatedAt = now;
    }

    void addRole(Role role) {
        roles.add(role);
    }

    void removeRole(RoleName name) {
        roles.removeIf(r -> r.getName() == name);
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasRole(RoleName name) {
        return roles.stream().anyMatch(r -> r.getName() == name);
    }

    /** Role names in a stable order (CUSTOMER, MERCHANT, COURIER, DISPATCHER). */
    public List<RoleName> roleNames() {
        return roles.stream().map(Role::getName).sorted(Comparator.naturalOrder()).toList();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
