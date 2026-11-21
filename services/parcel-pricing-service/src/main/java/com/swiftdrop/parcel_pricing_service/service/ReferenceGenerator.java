package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.common.ApiException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Tracking numbers such as SWD-2026-000125, taken from the database sequence
 * parcel_pricing.parcel_ref_seq. The sequence is the only source: if it cannot be read the booking
 * fails, because a guessed number could duplicate an existing parcel's reference.
 */
@Component
public class ReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateTrackingNumber() {
        Long next;
        try {
            next = jdbcTemplate.queryForObject("SELECT nextval('parcel_pricing.parcel_ref_seq')", Long.class);
        } catch (DataAccessException ex) {
            next = null;
        }
        if (next == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "REFERENCE_UNAVAILABLE",
                    "A tracking number could not be created right now. Please try again.");
        }
        return String.format("SWD-%d-%06d", Year.now().getValue(), next);
    }

    /** RCP-2026-000125 for SWD-2026-000125, so the receipt and the parcel share one number. */
    public String generateReceiptNumber(String trackingNumber) {
        if (trackingNumber == null || !trackingNumber.startsWith("SWD-")) {
            throw new IllegalArgumentException("A receipt needs the parcel's SWD tracking number");
        }
        return "RCP-" + trackingNumber.substring(4);
    }
}
