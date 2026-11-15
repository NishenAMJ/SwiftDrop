package com.swiftdrop.parcel_pricing_service.service;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import com.swiftdrop.parcel_pricing_service.client.PaymentSettlementClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingCancellationServiceTest {
    private final ParcelRepository repo=mock(ParcelRepository.class);
    private final BookingCancellationService service=new BookingCancellationService(repo);
    private Jwt jwt(UUID id) { return Jwt.withTokenValue("test").header("alg","RS256").subject(id.toString()).build(); }
    private Parcel parcel() { var p=new Parcel(); p.setCustomerId(UUID.randomUUID()); when(repo.findById(p.getId())).thenReturn(Optional.of(p)); return p; }
    @Test void unauthenticatedIsRejectedBeforeLookup() {
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ApiException.class,()->service.eligibility(UUID.randomUUID(),null)).getStatus());
        verifyNoInteractions(repo);
    }
    @Test void anotherCustomerIsRejected() {
        var p=parcel(); assertEquals(HttpStatus.FORBIDDEN,assertThrows(ApiException.class,()->service.eligibility(p.getId(),jwt(UUID.randomUUID()))).getStatus());
    }
    @Test void unknownPickupAndJourneyCannotAuthorizeOrCharge() {
        var p=parcel(); var result=service.eligibility(p.getId(),jwt(p.getCustomerId()));
        assertFalse(result.eligible()); assertNull(result.refundableAmount()); assertNull(result.scheduledPickupTime());
        assertThrows(ApiException.class,()->service.initiate(p.getId(),jwt(p.getCustomerId())));
        assertEquals("BOOKED",p.getStatus()); verify(repo,never()).save(any());
    }
    @Test void assignedDoesNotProveJourneyHasNotStarted() {
        var p=parcel(); p.setStatus("ASSIGNED"); assertFalse(service.eligibility(p.getId(),jwt(p.getCustomerId())).eligible());
    }
    @Test void cancelledCannotBeReactivated() {
        var p=new Parcel(); p.setStatus("CANCELLED"); assertThrows(IllegalStateException.class,()->p.setStatus("BOOKED"));
        assertEquals("CANCELLED",p.getStatus());
    }
    @Test void legacyPaymentAdapterCannotReportFalseSuccess() {
        var client=new PaymentSettlementClient();
        assertThrows(UnsupportedOperationException.class,()->client.initiatePayment(UUID.randomUUID(),BigDecimal.ONE));
        assertThrows(UnsupportedOperationException.class,()->client.deductFee(UUID.randomUUID(),BigDecimal.ONE));
    }
}
