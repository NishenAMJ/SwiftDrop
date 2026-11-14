package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.CustomerProfileDto;
import com.swiftdrop.parcel_pricing_service.dto.UpdateProfileRequest;
import com.swiftdrop.parcel_pricing_service.entity.CustomerProfile;
import com.swiftdrop.parcel_pricing_service.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CustomerProfileService {

    private final CustomerProfileRepository profileRepository;

    public CustomerProfileService(CustomerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public CustomerProfileDto getProfile(UUID customerId) {
        return profileRepository.findById(customerId)
                .map(CustomerProfileDto::from)
                .orElseGet(() -> createDefaultProfile(customerId));
    }

    public CustomerProfileDto updateProfile(UUID customerId, UpdateProfileRequest request) {
        CustomerProfile profile = profileRepository.findById(customerId)
                .orElseGet(() -> new CustomerProfile(customerId, request.fullName(), request.email()));

        profile.setFullName(request.fullName());
        profile.setEmail(request.email());
        profile.setPhoneNumber(request.phoneNumber());
        profile.setStreetAddress(request.streetAddress());
        profile.setCity(request.city());
        profile.setPostalCode(request.postalCode());

        // Update persistent avatar base64 image in PostgreSQL
        if (request.avatarBase64() != null) {
            profile.setAvatarBase64(request.avatarBase64());
        }

        CustomerProfile saved = profileRepository.save(profile);
        return CustomerProfileDto.from(saved);
    }

    private CustomerProfileDto createDefaultProfile(UUID customerId) {
        return new CustomerProfileDto(
                customerId,
                "SwiftDrop Customer",
                "customer@swiftdrop.lk",
                "+94 77 000 0000",
                "123 Main Street",
                "Colombo",
                "00100",
                null,
                null
        );
    }
}

