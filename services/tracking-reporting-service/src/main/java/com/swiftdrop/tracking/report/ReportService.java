package com.swiftdrop.tracking.report;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private final ReportRepository reports;

    public ReportService(ReportRepository reports) {
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary(LocalDate startDate, LocalDate endDate) {
        return reports.summary(ReportPeriod.of(startDate, endDate));
    }

    @Transactional(readOnly = true)
    public StatusBreakdownResponse statusBreakdown(LocalDate startDate, LocalDate endDate) {
        return reports.statusBreakdown(ReportPeriod.of(startDate, endDate));
    }

    @Transactional(readOnly = true)
    public DeliveryCountsResponse dailyDeliveries(LocalDate startDate, LocalDate endDate) {
        return reports.dailyDeliveries(ReportPeriod.of(startDate, endDate));
    }

    @Transactional(readOnly = true)
    public DeliveryCountsResponse monthlyDeliveries(LocalDate startDate, LocalDate endDate) {
        return reports.monthlyDeliveries(ReportPeriod.of(startDate, endDate));
    }
}
