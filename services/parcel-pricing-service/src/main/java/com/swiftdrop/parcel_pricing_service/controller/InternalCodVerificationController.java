package com.swiftdrop.parcel_pricing_service.controller;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.CodVerificationDto;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Service-to-service lookup for Payment & Settlement: the authoritative COD amount of a booking.
 *
 * <p>Payment has no user token when it consumes delivery.cod-collected, so this endpoint is guarded by a
 * shared key (header X-Internal-Key, value swiftdrop.internal.api-key) instead of a login. With no key
 * configured the endpoint does not exist, so a default deployment exposes nothing. It is not routed
 * through the API gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/parcels")
public class InternalCodVerificationController {

    private final ParcelRepository parcelRepository;
    private final String expectedKey;

    public InternalCodVerificationController(ParcelRepository parcelRepository,
                                             @Value("${swiftdrop.internal.api-key:}") String expectedKey) {
        this.parcelRepository = parcelRepository;
        this.expectedKey = expectedKey == null ? "" : expectedKey;
    }

    @GetMapping("/{bookingId}/cod-verification")
    @Transactional(readOnly = true)
    public CodVerificationDto codVerification(@PathVariable String bookingId,
                                              @RequestHeader(value = "X-Internal-Key", required = false) String key) {
        if (expectedKey.isBlank()) {
            throw ApiException.notFound("Not found.");
        }
        if (!sameKey(key)) {
            throw ApiException.unauthorized("A valid internal key is required.");
        }
        UUID id;
        try {
            id = UUID.fromString(bookingId);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.notFound("No booking has this id.");
        }
        return parcelRepository.findById(id)
                .map(CodVerificationDto::from)
                .orElseThrow(() -> ApiException.notFound("No booking has this id."));
    }

    /** Constant-time comparison, so response timing does not leak how much of the key matched. */
    private boolean sameKey(String supplied) {
        return supplied != null && MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }
}
