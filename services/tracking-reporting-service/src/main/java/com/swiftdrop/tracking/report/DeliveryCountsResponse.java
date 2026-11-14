package com.swiftdrop.tracking.report;

import java.time.LocalDate;
import java.util.List;

public record DeliveryCountsResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<DeliveryCountResponse> counts) {

    public DeliveryCountsResponse {
        counts = List.copyOf(counts);
    }
}
