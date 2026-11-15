package com.swiftdrop.tracking.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportSummaryResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalParcelsBooked,
        long parcelsCurrentlyInTransit,
        long deliveredParcels,
        long failedDeliveries,
        BigDecimal deliverySuccessRate) {
}
