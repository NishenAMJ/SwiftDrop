package com.swiftdrop.tracking.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.tracking.common.InvalidTrackingEventException;
import com.swiftdrop.tracking.event.TrackingEventData;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Projection rules for a single parcel: ordering, location retention and identity checks. */
class TrackingRecordTests {
    private final UUID parcelId = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-11-14T10:00:00Z");

    private TrackingEventData data(String status, String location) {
        return new TrackingEventData(parcelId, "SWD-2026-100001", status, "Update", location,
                null, null, null, null, null, null, null, null);
    }

    @Test
    void newerEventUpdatesStatusLocationAndTimestamp() {
        TrackingRecord record = TrackingRecord.fromFirstEvent(data("BOOKED", null), T0);
        assertThat(record.apply(data("AT_WAREHOUSE", "Colombo hub"), T0.plusSeconds(60))).isTrue();
        assertThat(record.toState().status()).isEqualTo("AT_WAREHOUSE");
        assertThat(record.latestSafeLocationLabel()).isEqualTo("Colombo hub");
        assertThat(record.lastUpdatedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void outOfOrderEventIsIgnoredAndDoesNotRegressTheStatus() {
        TrackingRecord record = TrackingRecord.fromFirstEvent(data("OUT_FOR_DELIVERY", "Kandy"), T0);
        assertThat(record.apply(data("AT_WAREHOUSE", "Colombo hub"), T0.minusSeconds(1))).isFalse();
        assertThat(record.currentStatus()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(record.latestSafeLocationLabel()).isEqualTo("Kandy");
        assertThat(record.lastUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void eventWithTheSameTimestampIsAppliedSoTiesFollowArrivalOrder() {
        TrackingRecord record = TrackingRecord.fromFirstEvent(data("ASSIGNED", null), T0);
        assertThat(record.apply(data("PICKED_UP", null), T0)).isTrue();
        assertThat(record.currentStatus()).isEqualTo("PICKED_UP");
    }

    @Test
    void eventWithoutALocationKeepsTheLastKnownSafeLocation() {
        TrackingRecord record = TrackingRecord.fromFirstEvent(data("AT_WAREHOUSE", "Colombo hub"), T0);
        record.apply(data("OUT_FOR_DELIVERY", null), T0.plusSeconds(5));
        assertThat(record.latestSafeLocationLabel()).isEqualTo("Colombo hub");
    }

    @Test
    void eventForAnotherParcelOrTrackingNumberIsRejected() {
        TrackingRecord record = TrackingRecord.fromFirstEvent(data("BOOKED", null), T0);
        var otherParcel = new TrackingEventData(UUID.randomUUID(), "SWD-2026-100001", "ASSIGNED", "x", null,
                null, null, null, null, null, null, null, null);
        var otherNumber = new TrackingEventData(parcelId, "SWD-2026-999999", "ASSIGNED", "x", null,
                null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> record.apply(otherParcel, T0.plusSeconds(1))).isInstanceOf(InvalidTrackingEventException.class);
        assertThatThrownBy(() -> record.apply(otherNumber, T0.plusSeconds(1))).isInstanceOf(InvalidTrackingEventException.class);
        assertThat(record.currentStatus()).isEqualTo("BOOKED");
    }
}
