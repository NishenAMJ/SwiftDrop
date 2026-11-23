package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.event.ParcelEventPublisher;
import com.swiftdrop.parcel_pricing_service.repository.BookingReceiptRepository;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import com.swiftdrop.parcel_pricing_service.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlineBookingDisabledTest {
    @Test void sharedBookingWriterRejectsOnlineWithoutSavingOrConvertingToCod() {
        ParcelRepository parcels = mock(ParcelRepository.class);
        BookingReceiptRepository receipts = mock(BookingReceiptRepository.class);
        ParcelEventPublisher events = mock(ParcelEventPublisher.class);
        BookingService service = new BookingService(parcels, receipts, mock(ReferenceGenerator.class),
                mock(PricingService.class), mock(ZoneRepository.class), mock(IdentityClient.class), events);
        Parcel parcel = new Parcel();
        parcel.setPaymentMethod("ONLINE");
        ApiException error = assertThrows(ApiException.class, () -> service.saveNewBooking(parcel));
        assertEquals("ONLINE_PAYMENTS_DISABLED", error.getCode());
        assertEquals("ONLINE", parcel.getPaymentMethod());
        verifyNoInteractions(parcels, receipts, events);
    }
}
