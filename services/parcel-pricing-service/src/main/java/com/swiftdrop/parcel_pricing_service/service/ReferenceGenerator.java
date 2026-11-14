package com.swiftdrop.parcel_pricing_service.service;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong inMemoryFallback = new AtomicLong(100);

    public ReferenceGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateTrackingNumber() {
        int currentYear = Year.now().getValue();
        long seqValue;
        try {
            Long nextVal = jdbcTemplate.queryForObject(
                "SELECT nextval('parcel_pricing.parcel_ref_seq')", Long.class);
            seqValue = (nextVal != null) ? nextVal : inMemoryFallback.incrementAndGet();
        } catch (DataAccessException ex) {
            seqValue = inMemoryFallback.incrementAndGet();
        }

        return String.format("SWD-%d-%06d", currentYear, seqValue);
    }

    public String generateReceiptNumber(String trackingNumber) {
        if (trackingNumber != null && trackingNumber.startsWith("SWD-")) {
            return trackingNumber.replaceFirst("SWD-", "RCP-");
        }
        return "RCP-" + Year.now().getValue() + "-" + inMemoryFallback.incrementAndGet();
    }
}

