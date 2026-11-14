package com.swiftdrop.tracking.report;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {

    private static final String BOOKING_COHORT = """
            WITH cohort AS (
                SELECT DISTINCT parcel_id
                FROM tracking_reporting.tracking_history
                WHERE event_type = 'parcel.booked'
                  AND event_time >= :startTime
                  AND event_time < :endTime
            )
            """;

    private final JdbcClient jdbcClient;

    public ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public ReportSummaryResponse summary(ReportPeriod period) {
        String sql = BOOKING_COHORT + """
                SELECT
                    COUNT(*) AS total_booked,
                    COUNT(*) FILTER (WHERE records.current_status = 'IN_TRANSIT') AS in_transit,
                    COUNT(*) FILTER (WHERE records.current_status = 'DELIVERED') AS delivered,
                    COUNT(*) FILTER (WHERE records.current_status = 'DELIVERY_FAILED') AS failed,
                    CASE
                        WHEN COUNT(*) FILTER (
                            WHERE records.current_status IN ('DELIVERED', 'DELIVERY_FAILED')) = 0
                        THEN 0.00
                        ELSE ROUND(
                            COUNT(*) FILTER (WHERE records.current_status = 'DELIVERED') * 100.0
                            / COUNT(*) FILTER (
                                WHERE records.current_status IN ('DELIVERED', 'DELIVERY_FAILED')),
                            2)
                    END AS success_rate
                FROM cohort
                JOIN tracking_reporting.tracking_records records USING (parcel_id)
                """;

        return jdbcClient.sql(sql)
                .param("startTime", period.startInclusive().atOffset(ZoneOffset.UTC))
                .param("endTime", period.endExclusive().atOffset(ZoneOffset.UTC))
                .query((resultSet, rowNumber) -> new ReportSummaryResponse(
                        period.startDate(),
                        period.endDate(),
                        resultSet.getLong("total_booked"),
                        resultSet.getLong("in_transit"),
                        resultSet.getLong("delivered"),
                        resultSet.getLong("failed"),
                        resultSet.getBigDecimal("success_rate")))
                .single();
    }

    public StatusBreakdownResponse statusBreakdown(ReportPeriod period) {
        String sql = BOOKING_COHORT + """
                SELECT records.current_status AS status, COUNT(*) AS total
                FROM cohort
                JOIN tracking_reporting.tracking_records records USING (parcel_id)
                GROUP BY records.current_status
                ORDER BY records.current_status
                """;

        List<StatusTotalResponse> statuses = jdbcClient.sql(sql)
                .param("startTime", period.startInclusive().atOffset(ZoneOffset.UTC))
                .param("endTime", period.endExclusive().atOffset(ZoneOffset.UTC))
                .query((resultSet, rowNumber) -> new StatusTotalResponse(
                        resultSet.getString("status"),
                        resultSet.getLong("total")))
                .list();
        return new StatusBreakdownResponse(period.startDate(), period.endDate(), statuses);
    }

    public DeliveryCountsResponse dailyDeliveries(ReportPeriod period) {
        return deliveryCounts(period, "CAST(event_time AT TIME ZONE 'UTC' AS date)");
    }

    public DeliveryCountsResponse monthlyDeliveries(ReportPeriod period) {
        return deliveryCounts(period, "CAST(date_trunc('month', event_time AT TIME ZONE 'UTC') AS date)");
    }

    private DeliveryCountsResponse deliveryCounts(ReportPeriod period, String periodExpression) {
        String sql = """
                SELECT %s AS report_period, COUNT(DISTINCT parcel_id) AS delivered_parcels
                FROM tracking_reporting.tracking_history
                WHERE event_type = 'delivery.delivered'
                  AND event_time >= :startTime
                  AND event_time < :endTime
                GROUP BY report_period
                ORDER BY report_period
                """.formatted(periodExpression);

        List<DeliveryCountResponse> counts = jdbcClient.sql(sql)
                .param("startTime", period.startInclusive().atOffset(ZoneOffset.UTC))
                .param("endTime", period.endExclusive().atOffset(ZoneOffset.UTC))
                .query((resultSet, rowNumber) -> new DeliveryCountResponse(
                        resultSet.getObject("report_period", LocalDate.class),
                        resultSet.getLong("delivered_parcels")))
                .list();
        return new DeliveryCountsResponse(period.startDate(), period.endDate(), counts);
    }
}
