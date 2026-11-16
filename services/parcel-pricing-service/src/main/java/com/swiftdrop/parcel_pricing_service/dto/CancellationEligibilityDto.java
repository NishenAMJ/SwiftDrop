package com.swiftdrop.parcel_pricing_service.dto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
/** Null financial/schedule values mean unverified, never zero or a promised refund. */
public record CancellationEligibilityDto(
    boolean eligible, String reason, BigDecimal cancellationFee, OffsetDateTime earliestEligiblePickup,
    UUID bookingId, String currentBookingStatus, OffsetDateTime scheduledPickupTime,
    BigDecimal originalPaidAmount, BigDecimal refundableAmount, String paymentMethod,
    String paymentStatus, BookingResponseDto booking
) {
    public static CancellationEligibilityDto eligible(BigDecimal fee, OffsetDateTime earliest) {
        return new CancellationEligibilityDto(true,null,fee,earliest,null,null,null,null,null,null,null,null);
    }
    public static CancellationEligibilityDto ineligible(String reason) {
        return new CancellationEligibilityDto(false,reason,null,null,null,null,null,null,null,null,null,null);
    }
}
