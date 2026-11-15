package com.swiftdrop.parcel_pricing_service.service;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.CancellationEligibilityDto;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

/** Reject cancellation when authoritative pickup/journey verification is unavailable. */
@Service
@Transactional(readOnly=true)
public class BookingCancellationService {
    private final ParcelRepository parcels;
    public BookingCancellationService(ParcelRepository parcels) { this.parcels=parcels; }
    public Parcel owned(UUID id, Jwt jwt) {
        UUID customer;
        try {
            if(jwt==null) throw new IllegalArgumentException();
            customer=UUID.fromString(jwt.getSubject());
        } catch(IllegalArgumentException e) { throw ApiException.unauthorized("A valid customer login is required."); }
        Parcel p=parcels.findById(id).orElseThrow(()->ApiException.notFound("Booking was not found."));
        if(!customer.equals(p.getCustomerId())) throw ApiException.forbidden("You do not own this booking.");
        return p;
    }
    public CancellationEligibilityDto eligibility(UUID id, Jwt jwt) {
        Parcel p=owned(id,jwt);
        String reason="Cancellation is unavailable because the scheduled pickup time and courier journey cannot be verified.";
        if("CANCELLED".equals(p.getStatus())) reason="This booking is already cancelled.";
        else if(!java.util.Set.of("BOOKED","ASSIGNED").contains(p.getStatus()))
            reason="The booking has not been confirmed or has progressed beyond the cancellation stage.";
        return new CancellationEligibilityDto(false,reason,null,null,id,p.getStatus(),null,null,null,
            p.getPaymentMethod(),p.getPaymentStatus(),com.swiftdrop.parcel_pricing_service.dto.BookingResponseDto.from(p,null));
    }
    public void initiate(UUID id, Jwt jwt) {
        throw ApiException.conflict("CANCELLATION_UNAVAILABLE", eligibility(id,jwt).reason());
    }
}
