package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.dto.CustomerProfileDto;
import com.swiftdrop.parcel_pricing_service.dto.UpdateProfileRequest;
import com.swiftdrop.parcel_pricing_service.entity.CustomerProfile;
import com.swiftdrop.parcel_pricing_service.repository.CustomerProfileRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Customer profile = Identity account (name, email, phone; owned by Member 1's service)
 * + what only this service needs (default pickup address and profile photo).
 *
 * <p>Name/email/phone are always read from Identity and written back to Identity, so the two
 * services cannot drift apart. The copy kept in customer_profiles is only a fallback for when
 * Identity is briefly unreachable.
 */
@Service
@Transactional
public class CustomerProfileService {

    private final CustomerProfileRepository profileRepository;
    private final IdentityClient identityClient;

    public CustomerProfileService(CustomerProfileRepository profileRepository, IdentityClient identityClient) {
        this.profileRepository = profileRepository;
        this.identityClient = identityClient;
    }

    public CustomerProfileDto getProfile(UUID customerId, Jwt jwt) {
        Optional<CustomerProfile> stored = profileRepository.findById(customerId);
        Optional<IdentityUser> account = identityClient.currentUser(jwt);
        if (account.isEmpty()) {
            return stored.map(CustomerProfileDto::from).orElseGet(() -> CustomerProfileDto.empty(customerId));
        }
        IdentityUser user = account.get();
        // Refresh the fallback copy only if it already exists and changed.
        stored.filter(p -> !Objects.equals(p.getFullName(), user.fullName())
                        || !Objects.equals(p.getEmail(), user.email())
                        || !Objects.equals(p.getPhoneNumber(), user.phoneNumber()))
                .ifPresent(p -> copyAccount(p, user));
        return CustomerProfileDto.of(customerId, user, stored.orElse(null));
    }

    public CustomerProfileDto updateProfile(UUID customerId, UpdateProfileRequest request, Jwt jwt) {
        IdentityUser current = identityClient.currentUser(jwt).orElse(null);
        String fullName = request.fullName().trim();
        String phone = IdentityClient.toE164(blankToNull(request.phoneNumber()));

        boolean nameChanged = current == null || !fullName.equals(current.fullName());
        boolean phoneChanged = phone != null && (current == null || !phone.equals(current.phoneNumber()));
        final IdentityUser user = (nameChanged || phoneChanged)
                ? identityClient.updateCurrentUser(jwt, nameChanged ? fullName : null, phoneChanged ? phone : null)
                : current;

        CustomerProfile profile = profileRepository.findById(customerId)
                .orElseGet(() -> new CustomerProfile(customerId, user.fullName(), user.email()));
        copyAccount(profile, user);
        profile.setStreetAddress(blankToNull(request.streetAddress()));
        profile.setCity(blankToNull(request.city()));
        profile.setPostalCode(blankToNull(request.postalCode()));
        if (request.avatarBase64() != null) {
            profile.setAvatarBase64(request.avatarBase64().isBlank() ? null : request.avatarBase64());
        }
        CustomerProfile saved = profileRepository.save(profile);
        return CustomerProfileDto.of(customerId, user, saved);
    }

    private static void copyAccount(CustomerProfile profile, IdentityUser user) {
        if (user.fullName() != null) profile.setFullName(user.fullName());
        if (user.email() != null) profile.setEmail(user.email());
        profile.setPhoneNumber(user.phoneNumber());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
