package com.swiftdrop.tracking.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.swiftdrop.tracking.common.ApiException;

record ReportPeriod(LocalDate startDate, LocalDate endDate, Instant startInclusive, Instant endExclusive) {

    private static final Instant EARLIEST = Instant.EPOCH;
    private static final Instant LATEST = Instant.parse("9999-12-31T23:59:59.999999Z");

    static ReportPeriod of(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw ApiException.validation("startDate must not be after endDate.");
        }
        Instant start = startDate == null
                ? EARLIEST
                : startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate == null
                ? LATEST
                : endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new ReportPeriod(startDate, endDate, start, end);
    }
}
