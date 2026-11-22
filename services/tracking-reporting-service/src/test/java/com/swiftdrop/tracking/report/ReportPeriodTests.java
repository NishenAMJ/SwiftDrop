package com.swiftdrop.tracking.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.tracking.common.ApiException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Report date filters: inclusive start, exclusive end of the end date, open-ended bounds. */
class ReportPeriodTests {

    @Test
    void singleDayCoversTheWholeUtcDay() {
        ReportPeriod period = ReportPeriod.of(LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-14"));
        assertThat(period.startInclusive()).isEqualTo(Instant.parse("2026-11-14T00:00:00Z"));
        assertThat(period.endExclusive()).isEqualTo(Instant.parse("2026-11-15T00:00:00Z"));
    }

    @Test
    void missingBoundsAreOpenEnded() {
        ReportPeriod open = ReportPeriod.of(null, null);
        assertThat(open.startInclusive()).isEqualTo(Instant.EPOCH);
        assertThat(open.endExclusive()).isAfter(Instant.parse("9999-01-01T00:00:00Z"));

        assertThat(ReportPeriod.of(LocalDate.parse("2026-11-01"), null).startInclusive())
                .isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(ReportPeriod.of(null, LocalDate.parse("2026-11-30")).endExclusive())
                .isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void startAfterEndIsRejected() {
        assertThatThrownBy(() -> ReportPeriod.of(LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-14")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void leapDayAndYearEndBoundariesRollOverCorrectly() {
        assertThat(ReportPeriod.of(null, LocalDate.parse("2028-02-28")).endExclusive())
                .isEqualTo(Instant.parse("2028-02-29T00:00:00Z"));
        assertThat(ReportPeriod.of(null, LocalDate.parse("2026-12-31")).endExclusive())
                .isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    }

    /**
     * Documents current behaviour: day boundaries are UTC, not Asia/Colombo (UTC+05:30). An event at
     * 01:00 Colombo time on 14 Nov (19:30 UTC on 13 Nov) is reported under 13 Nov.
     * See docs/testing/TEST_REPORT_AND_COVERAGE_SUMMARY.md (risks).
     */
    @Test
    void dayBoundariesAreUtcNotColomboLocalTime() {
        ReportPeriod fourteenth = ReportPeriod.of(LocalDate.parse("2026-11-14"), LocalDate.parse("2026-11-14"));
        Instant earlyColomboMorning = Instant.parse("2026-11-13T19:30:00Z");
        assertThat(earlyColomboMorning).isBefore(fourteenth.startInclusive());
    }
}
