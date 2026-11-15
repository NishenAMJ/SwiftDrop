package com.swiftdrop.tracking.report;

import java.time.LocalDate;
import java.util.List;

public record StatusBreakdownResponse(
        LocalDate startDate,
        LocalDate endDate,
        List<StatusTotalResponse> statuses) {

    public StatusBreakdownResponse {
        statuses = List.copyOf(statuses);
    }
}
