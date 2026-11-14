package com.swiftdrop.tracking.report;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/reports", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return reports.summary(startDate, endDate);
    }

    @GetMapping("/deliveries/daily")
    public DeliveryCountsResponse dailyDeliveries(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return reports.dailyDeliveries(startDate, endDate);
    }

    @GetMapping("/deliveries/monthly")
    public DeliveryCountsResponse monthlyDeliveries(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return reports.monthlyDeliveries(startDate, endDate);
    }

    @GetMapping("/status-breakdown")
    public StatusBreakdownResponse statusBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate endDate) {
        return reports.statusBreakdown(startDate, endDate);
    }
}
