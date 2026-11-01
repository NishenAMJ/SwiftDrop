package com.swiftdrop.identity.user;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.event.EventRecorder;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final EventRecorder events;
    private final Clock clock;

    /** Compared against when the email is unknown, so both failures take the same time. */
    private final String dummyPasswordHash;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            EventRecorder events, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-" + UUID.randomUUID());
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Convenience for internal callers creating a customer; public registration requires a role. */
    @Transactional
    public User registerCustomer(String email, String password, String fullName, String phoneNumber) {
        return register(email, password, fullName, phoneNumber, RoleName.CUSTOMER);
    }

    @Transactional
    public User register(String email, String password, String fullName, String phoneNumber, RoleName role) {
        requirePublicRole(role);
        PasswordPolicy.check("password", password);
        User user = newUser(email, fullName, phoneNumber, password, role);
        events.record("user.registered", eventData(user, "PASSWORD"));
        return user;
    }

    /**
     * Google login, after Google has confirmed the email address. In order:
     * <ol>
     *   <li>the user who already logged in with this Google account;</li>
     *   <li>otherwise the account with the same email, which gets linked to Google;</li>
     *   <li>otherwise a new account awaiting role selection without a password ({@code user.registered}).</li>
     * </ol>
     * The caller must only pass emails that Google reports as verified; linking an unverified
     * email would let anyone take over the account registered with it.
     */
    @Transactional
    public User signInWithGoogle(String googleSubject, String verifiedEmail, String name) {
        User known = users.findByGoogleSubject(googleSubject).orElse(null);
        if (known != null) {
            return known;
        }
        Instant now = clock.instant();
        String email = normalizeEmail(verifiedEmail);
        User sameEmail = users.findByEmail(email).orElse(null);
        if (sameEmail != null) {
            if (sameEmail.getGoogleSubject() != null) {
                // The email belongs to an account already linked to a different Google account.
                throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_MISMATCH",
                        "This email is linked to a different Google account.");
            }
            sameEmail.linkGoogle(googleSubject, now);
            return users.saveAndFlush(sameEmail);
        }

        User user = new User(email, displayName(name, email), null, now);
        user.linkGoogle(googleSubject, now);

        try {
            users.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException ex) {
            // The same person finishing Google login twice at once: the first one created the user.
            return users.findByGoogleSubject(googleSubject).orElseThrow(() -> ex);
        }
        events.record("user.registered", eventData(user, "GOOGLE"));
        return user;
    }

    private static String displayName(String name, String email) {
        String value = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.trim();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    /** A dispatcher creating another dispatcher, or the startup bootstrap creating the first one. */
    @Transactional
    public User createDispatcher(String email, String fullName, String phoneNumber, String initialPassword) {
        PasswordPolicy.check("initialPassword", initialPassword);
        User user = newUser(email, fullName, phoneNumber, initialPassword, RoleName.DISPATCHER);
        user.setMustChangePassword(true);
        events.record("user.registered", eventData(user, "PASSWORD"));
        return user;
    }

    /**
     * Checks email and password. Unknown email and wrong password give the same error, so callers
     * can't discover which emails are registered. Suspension is reported only after a correct
     * password.
     */
    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        User user = users.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null || !user.hasPassword()) {
            // Same BCrypt cost as a real check, so response time doesn't reveal the email exists.
            passwordEncoder.matches(password, dummyPasswordHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }
        requireActive(user);
        return user;
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
    }

    public void requireActive(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is suspended.");
        }
    }

    @Transactional
    public User updateProfile(UUID userId, String fullName, String phoneNumber) {
        User user = get(userId);
        user.updateProfile(fullName == null ? null : fullName.trim(), phoneNumber, clock.instant());
        users.saveAndFlush(user);
        events.record("user.updated", eventData(user, null));
        return user;
    }

    /**
     * Sets a new password. The current password is required whenever the account already has
     * one; a Google-only account may set its first password without it.
     */
    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = get(userId);
        if (user.hasPassword()
                && (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVALID_CREDENTIALS", "The current password is incorrect.");
        }
        PasswordPolicy.check("newPassword", newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword), clock.instant());
        user.setMustChangePassword(false);
        return users.saveAndFlush(user);
    }

    /**
     * Gives the user a role (merchant or courier approval). Takes effect in their next access token.
     * Publishes {@code user.updated} if anything changed.
     */
    @Transactional
    public void grantRole(UUID userId, RoleName roleName) {
        User user = get(userId);
        if (user.hasRole(roleName)) {
            return;
        }
        user.addRole(roles.findByName(roleName)
            .orElseThrow(() -> new IllegalStateException("Role missing from database: " + roleName)));
        user.updateProfile(null, null, clock.instant());
        users.saveAndFlush(user);
        events.record("user.updated", eventData(user, null));
    }

    /** Removes a role (suspension). Publishes {@code user.updated} if anything changed. */
    @Transactional
    public void revokeRole(UUID userId, RoleName roleName) {
        User user = get(userId);
        if (!user.hasRole(roleName)) {
            return;
        }
        user.removeRole(roleName);
        user.updateProfile(null, null, clock.instant());
        users.saveAndFlush(user);
        events.record("user.updated", eventData(user, null));
    }

    @Transactional(readOnly = true)
    public Page<User> search(RoleName role, UserStatus status, String text, Pageable pageable) {
        return users.findAll(UserSearch.matching(role, status, text), pageable);
    }

    @Transactional(readOnly = true)
    public boolean anyDispatcherExists() {
        return users.existsByRoles_Name(RoleName.DISPATCHER);
    }

    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return users.existsByEmail(normalizeEmail(email));
    }

    private User newUser(String email, String fullName, String phoneNumber, String password, RoleName roleName) {
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw emailTaken();
        }
        Instant now = clock.instant();
        User user = new User(normalizedEmail, fullName.trim(), phoneNumber, now);
        user.setPasswordHash(passwordEncoder.encode(password), now);
        user.assignRole(roleName);
        if (roleName == RoleName.CUSTOMER || roleName == RoleName.DISPATCHER) {
            user.addRole(roles.findByName(roleName).orElseThrow());
        }
        try {
            return users.saveAndFlush(user);
        }
        catch (DataIntegrityViolationException ex) {
            // Two registrations with the same email at the same moment: the unique constraint wins.
            throw emailTaken();
        }
    }

    private static void requirePublicRole(RoleName role) {
        if (role == null || role == RoleName.DISPATCHER) {
            throw ApiException.validation("role", "Choose customer, merchant or courier.");
        }
    }

    @Transactional
    public User selectRole(UUID userId, RoleName role) {
        requirePublicRole(role);
        User user = get(userId);
        requireActive(user);
        if (user.getAccountRole() != null || user.getGoogleSubject() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_ALREADY_ASSIGNED", "Your account role cannot be changed.");
        }
        user.assignRole(role);
        if (role == RoleName.CUSTOMER) user.addRole(roles.findByName(role).orElseThrow());
        users.saveAndFlush(user);
        events.record("user.updated", eventData(user, null));
        return user;
    }

    public void requireApplicant(UUID userId, RoleName role) {
        User user = get(userId);
        requireActive(user);
        if (user.getAccountRole() != role || user.isMustChangePassword()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This application does not match your account role.");
        }
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                "An account with this email already exists.");
    }

    /** {@code data} for user.registered / user.updated (contracts/events/identity-merchant). */
    private static Map<String, Object> eventData(User user, String authProvider) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId().toString());
        data.put("email", user.getEmail());
        data.put("fullName", user.getFullName());
        if (user.getPhoneNumber() != null) {
            data.put("phoneNumber", user.getPhoneNumber());
        }
        data.put("roles", user.roleNames().stream().map(Enum::name).toList());
        data.put("status", user.getStatus().name());
        if (user.getAccountRole() != null) data.put("accountRole", user.getAccountRole().name());
        if (authProvider != null) {
            data.put("authProvider", authProvider);
        }
        return data;
    }
}
