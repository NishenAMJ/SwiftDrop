package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.MerchantContact;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import com.swiftdrop.parcel_pricing_service.dto.AddressDto;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.entity.BookingReceipt;
import com.swiftdrop.parcel_pricing_service.entity.BookingStatus;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.Parcel;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateOrderException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidOrderException;
import com.swiftdrop.parcel_pricing_service.exception.OrderNotFoundException;
import com.swiftdrop.parcel_pricing_service.repository.BookingReceiptRepository;
import com.swiftdrop.parcel_pricing_service.repository.ParcelRepository;
import com.swiftdrop.parcel_pricing_service.security.MerchantPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.swiftdrop.parcel_pricing_service.controller.MerchantOrderTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MerchantOrderServiceTest {

    @Mock private ParcelRepository parcelRepository;
    @Mock private BookingReceiptRepository receiptRepository;
    @Mock private BookingService bookingService;
    @Mock private ReferenceGenerator referenceGenerator;
    @Mock private IdentityClient identityClient;

    private MerchantOrderService service;
    private final MerchantPrincipal merchant = new MerchantPrincipal(USER_ID, MERCHANT_ID, "token");

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(parcelRepository, receiptRepository, bookingService, referenceGenerator, identityClient);
        when(referenceGenerator.generateTrackingNumber()).thenReturn("SWD-2026-000010");
        when(identityClient.currentMerchant("token")).thenReturn(Optional.of(
                new MerchantContact(MERCHANT_ID, "Lanka Gadgets (Pvt) Ltd", "orders@lankagadgets.lk", "+94112345678")));
        // PricingService is the only fee formula; BookingService.applyQuote copies its quote onto the parcel.
        doAnswer(inv -> {
            Parcel p = inv.getArgument(0);
            p.setEstimatedFee(new BigDecimal("575.00"));
            p.setPricingZoneCode(inv.getArgument(2));
            return null;
        }).when(bookingService).applyQuote(any(), any(), any(), any());
        when(bookingService.saveNewBooking(any())).thenAnswer(inv -> {
            Parcel p = inv.getArgument(0);
            boolean cod = PaymentMethod.COD.name().equals(p.getPaymentMethod());
            p.setStatus(cod ? BookingStatus.BOOKED : BookingStatus.CHECKOUT_DRAFT);
            p.setPaymentStatus(cod ? "PENDING" : "UNPAID");
            return new BookingService.NewBooking(p, cod ? new BookingReceipt("RCP-2026-000010", p, p.getEstimatedFee(), "LKR") : null);
        });
    }

    private Parcel savedParcel() {
        ArgumentCaptor<Parcel> captor = ArgumentCaptor.forClass(Parcel.class);
        verify(bookingService).saveNewBooking(captor.capture());
        return captor.getValue();
    }

    @Test
    void codOrder_becomesAMerchantParcelPricedByPricingService() {
        MerchantOrderResponse response = service.createOrder(merchant, codRequest("SHOP-1001"), OrderSource.DASHBOARD);

        Parcel parcel = savedParcel();
        assertThat(parcel.getChannel()).isEqualTo("MERCHANT_API");
        assertThat(parcel.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(parcel.getCustomerId()).isEqualTo(USER_ID);
        assertThat(parcel.getOrderSource()).isEqualTo(OrderSource.DASHBOARD);
        assertThat(parcel.getTrackingNumber()).isEqualTo("SWD-2026-000010");
        assertThat(parcel.getCodAmount()).isEqualByComparingTo("2500.00");
        assertThat(parcel.getPaymentMethod()).isEqualTo("COD");
        assertThat(parcel.getPickupAddress().getCity()).isEqualTo("Colombo");
        assertThat(parcel.getDeliveryAddress().getZoneCode()).isEqualTo("KANDY");
        assertThat(parcel.getCustomerName()).isEqualTo("Lanka Gadgets (Pvt) Ltd");
        assertThat(parcel.getCustomerEmail()).isEqualTo("orders@lankagadgets.lk");
        verify(bookingService).applyQuote(any(Parcel.class), eq(new BigDecimal("2.50")), eq("KANDY"), eq(DeliveryType.NORMAL));
        verify(bookingService).requireActiveZone("COLOMBO");

        assertThat(response.trackingNumber()).isEqualTo("SWD-2026-000010");
        assertThat(response.deliveryFee()).isEqualByComparingTo("575.00");
        assertThat(response.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(response.receiptNumber()).isEqualTo("RCP-2026-000010");
    }

    @Test
    void onlineOrder_waitsForPaymentAndKeepsUrgentNote() {
        MerchantOrderResponse response = service.createOrder(merchant, onlineRequest(null), OrderSource.DASHBOARD);

        Parcel parcel = savedParcel();
        assertThat(parcel.getUrgency()).isEqualTo("URGENT");
        assertThat(parcel.getUrgentNote()).isEqualTo("Before 5pm");
        assertThat(parcel.getCodAmount()).isNull();
        verify(bookingService).applyQuote(any(Parcel.class), any(), eq("KANDY"), eq(DeliveryType.URGENT));
        assertThat(response.status()).isEqualTo(BookingStatus.CHECKOUT_DRAFT);
        assertThat(response.receiptNumber()).isNull();
    }

    @Test
    void identityUnavailable_fallsBackToThePickupContact() {
        when(identityClient.currentMerchant(any())).thenReturn(Optional.empty());

        service.createOrder(merchant, codRequest(null), OrderSource.DASHBOARD);

        Parcel parcel = savedParcel();
        assertThat(parcel.getCustomerName()).isEqualTo("Lanka Gadgets");
        assertThat(parcel.getCustomerPhone()).isEqualTo("0112345678");
        assertThat(parcel.getCustomerEmail()).isNull();
    }

    @Test
    void deliveryZoneIsRequired() {
        AddressDto noZone = new AddressDto("Kamala Silva", "0777654321", "45 Lake Rd", "Kandy", null, " ");
        MerchantOrderRequest request = new MerchantOrderRequest(null, null, new BigDecimal("1.00"), null, null,
                PaymentMethod.COD, new BigDecimal("100.00"), pickup(), noZone);

        assertThatThrownBy(() -> service.createOrder(merchant, request, OrderSource.DASHBOARD))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "ZONE_REQUIRED");
        verify(bookingService, never()).saveNewBooking(any());
    }

    @Test
    void externalApiOrdersNeedAMerchantReference() {
        assertThatThrownBy(() -> service.createOrder(merchant, codRequest(null), OrderSource.EXTERNAL_API))
                .isInstanceOf(InvalidOrderException.class);
        verify(bookingService, never()).saveNewBooking(any());
    }

    @Test
    void codWithoutAmount_andOnlineWithAmount_areRejected() {
        MerchantOrderRequest codNoAmount = new MerchantOrderRequest(null, null, new BigDecimal("1.00"), null, null,
                PaymentMethod.COD, null, pickup(), delivery());
        MerchantOrderRequest onlineWithAmount = new MerchantOrderRequest(null, null, new BigDecimal("1.00"), null, null,
                PaymentMethod.ONLINE, new BigDecimal("10.00"), pickup(), delivery());

        assertThatThrownBy(() -> service.createOrder(merchant, codNoAmount, OrderSource.DASHBOARD))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> service.createOrder(merchant, onlineWithAmount, OrderSource.DASHBOARD))
                .isInstanceOf(InvalidOrderException.class);
        verify(bookingService, never()).saveNewBooking(any());
    }

    @Test
    void duplicateReference_isRejectedBeforeSaving() {
        when(parcelRepository.existsByMerchantIdAndMerchantReference(MERCHANT_ID, "SHOP-1001")).thenReturn(true);

        assertThatThrownBy(() -> service.createOrder(merchant, codRequest("SHOP-1001"), OrderSource.EXTERNAL_API))
                .isInstanceOf(DuplicateOrderException.class);
        verify(bookingService, never()).saveNewBooking(any());
    }

    @Test
    void concurrentDuplicateReference_atFlush_becomesDuplicateOrder() {
        doThrow(new DataIntegrityViolationException("dup",
                new SQLException("duplicate key value violates unique constraint \"uq_parcels_merchant_reference\"")))
                .when(parcelRepository).flush();

        assertThatThrownBy(() -> service.createOrder(merchant, codRequest("SHOP-1001"), OrderSource.EXTERNAL_API))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    void otherIntegrityErrors_areNotMaskedAsDuplicates() {
        doThrow(new DataIntegrityViolationException("other", new SQLException("ck_parcels_cod_amount")))
                .when(parcelRepository).flush();

        assertThatThrownBy(() -> service.createOrder(merchant, codRequest("SHOP-1001"), OrderSource.EXTERNAL_API))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void getOrder_ofAnotherMerchant_isNotFound() {
        UUID id = UUID.randomUUID();
        when(parcelRepository.findByIdAndMerchantId(id, MERCHANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(merchant, id)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getOrders_clampsPageSizeAndQueriesOnlyTheMerchant() {
        Parcel parcel = new Parcel();
        parcel.setMerchantId(MERCHANT_ID);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(parcelRepository.findByMerchantId(eq(MERCHANT_ID), pageable.capture()))
                .thenAnswer(inv -> new PageImpl<>(List.of(parcel), inv.getArgument(1), 1));
        when(receiptRepository.findByParcelId(any())).thenReturn(Optional.empty());

        Page<MerchantOrderResponse> page = service.getOrders(merchant, -3, 1000);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(MerchantOrderService.MAX_PAGE_SIZE);
    }
}
