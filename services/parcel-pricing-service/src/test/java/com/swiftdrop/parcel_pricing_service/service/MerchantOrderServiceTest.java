package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.dto.OrderAddressDto;
import com.swiftdrop.parcel_pricing_service.dto.OrderContactDto;
import com.swiftdrop.parcel_pricing_service.entity.DeliveryType;
import com.swiftdrop.parcel_pricing_service.entity.MerchantOrder;
import com.swiftdrop.parcel_pricing_service.entity.OrderAddress;
import com.swiftdrop.parcel_pricing_service.entity.OrderSource;
import com.swiftdrop.parcel_pricing_service.entity.OrderStatus;
import com.swiftdrop.parcel_pricing_service.entity.PaymentMethod;
import com.swiftdrop.parcel_pricing_service.exception.DuplicateOrderException;
import com.swiftdrop.parcel_pricing_service.exception.InvalidOrderException;
import com.swiftdrop.parcel_pricing_service.exception.OrderAccessDeniedException;
import com.swiftdrop.parcel_pricing_service.exception.OrderNotFoundException;
import com.swiftdrop.parcel_pricing_service.repository.MerchantOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {

    private static final String REFERENCE_CONSTRAINT_MESSAGE =
            "ERROR: duplicate key value violates unique constraint \"uq_merchant_orders_merchant_reference\"";

    @Mock
    private MerchantOrderRepository orderRepository;

    @Mock
    private DeliveryFeeCalculator feeCalculator;

    @InjectMocks
    private MerchantOrderService orderService;

    // ---------- fixtures ----------

    private static OrderContactDto contact(String name) {
        return new OrderContactDto(name, "+94771234567", "contact@shop.lk");
    }

    private static OrderAddressDto address(String city) {
        return new OrderAddressDto("12 Galle Road", null, city, "Colombo", "00300");
    }

    private static MerchantOrderRequest request(String reference, DeliveryType type, String note,
                                                PaymentMethod method, BigDecimal codAmount) {
        return new MerchantOrderRequest(reference, contact("Nimal Perera"), contact("Kamal Silva"),
                address("Colombo"), address("Kandy"), "Phone case", new BigDecimal("2.5"),
                type, note, method, codAmount);
    }

    private static MerchantOrder orderEntity(Long id, Long merchantId) {
        MerchantOrder order = new MerchantOrder();
        ReflectionTestUtils.setField(order, "id", id);
        order.setOrderNumber("MO-0000000" + id);
        order.setMerchantId(merchantId);
        order.setSenderName("Nimal Perera");
        order.setSenderPhone("+94771234567");
        order.setReceiverName("Kamal Silva");
        order.setReceiverPhone("0712345678");
        order.setPickupAddress(new OrderAddress("12 Galle Road", null, "Colombo", null, null));
        order.setDeliveryAddress(new OrderAddress("45 Temple Road", null, "Kandy", null, null));
        order.setParcelDescription("Phone case");
        order.setWeightKg(new BigDecimal("1.00"));
        order.setDeliveryType(DeliveryType.NORMAL);
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setDeliveryFee(new BigDecimal("300.00"));
        order.setStatus(OrderStatus.CREATED);
        order.setSource(OrderSource.DASHBOARD);
        return order;
    }

    private void stubSuccessfulCreate(BigDecimal fee) {
        when(feeCalculator.calculateFee(any())).thenReturn(fee);
        when(orderRepository.nextOrderSequence()).thenReturn(42L);
        when(orderRepository.saveAndFlush(any(MerchantOrder.class))).thenAnswer(invocation -> {
            MerchantOrder order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 10L);
            return order;
        });
    }

    // ---------- create ----------

    @Test
    void createOrder_dashboard_savesOrderForTheGivenMerchantWithServerSideFee() {
        stubSuccessfulCreate(new BigDecimal("450"));

        MerchantOrderResponse response = orderService.createOrder(7L,
                request("SHOP-1001", DeliveryType.NORMAL, "ignored for normal", PaymentMethod.COD, new BigDecimal("2500")),
                OrderSource.DASHBOARD);

        ArgumentCaptor<MerchantOrder> saved = ArgumentCaptor.forClass(MerchantOrder.class);
        verify(orderRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getMerchantId()).isEqualTo(7L);
        assertThat(saved.getValue().getUrgentNote()).isNull();
        assertThat(saved.getValue().getSource()).isEqualTo(OrderSource.DASHBOARD);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.orderNumber()).isEqualTo("MO-00000042");
        assertThat(response.merchantId()).isEqualTo(7L);
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.deliveryFee()).isEqualTo(new BigDecimal("450.00"));
        assertThat(response.codAmount()).isEqualTo(new BigDecimal("2500.00"));
        assertThat(response.weightKg()).isEqualTo(new BigDecimal("2.50"));
    }

    @Test
    void createOrder_passesWeightAndTypeToTheFeeCalculator() {
        stubSuccessfulCreate(new BigDecimal("600.00"));

        orderService.createOrder(7L, request(null, DeliveryType.URGENT, "Fragile", PaymentMethod.ONLINE, null),
                OrderSource.DASHBOARD);

        ArgumentCaptor<DeliveryFeeRequest> feeRequest = ArgumentCaptor.forClass(DeliveryFeeRequest.class);
        verify(feeCalculator).calculateFee(feeRequest.capture());
        assertThat(feeRequest.getValue().weightKg()).isEqualTo(new BigDecimal("2.50"));
        assertThat(feeRequest.getValue().deliveryType()).isEqualTo(DeliveryType.URGENT);
        assertThat(feeRequest.getValue().deliveryAddress().getCity()).isEqualTo("Kandy");
    }

    @Test
    void createOrder_urgentKeepsNote_onlineHasNoCodAmount() {
        stubSuccessfulCreate(new BigDecimal("600.00"));

        MerchantOrderResponse response = orderService.createOrder(7L,
                request(null, DeliveryType.URGENT, "  Fragile  ", PaymentMethod.ONLINE, null), OrderSource.DASHBOARD);

        assertThat(response.urgentNote()).isEqualTo("Fragile");
        assertThat(response.codAmount()).isNull();
        assertThat(response.merchantReference()).isNull();
    }

    @Test
    void createOrder_externalApi_recordsSource() {
        stubSuccessfulCreate(new BigDecimal("450.00"));

        MerchantOrderResponse response = orderService.createOrder(7L,
                request("WC-5001", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.EXTERNAL_API);

        assertThat(response.source()).isEqualTo(OrderSource.EXTERNAL_API);
        assertThat(response.merchantReference()).isEqualTo("WC-5001");
    }

    @Test
    void createOrder_externalApiWithoutReference_isRejected() {
        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("  ", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.EXTERNAL_API))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("merchantReference");

        verifyNoInteractions(orderRepository, feeCalculator);
    }

    @Test
    void createOrder_codWithoutAmount_isRejected() {
        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("A-1", DeliveryType.NORMAL, null, PaymentMethod.COD, null), OrderSource.DASHBOARD))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("codAmount");

        verifyNoInteractions(orderRepository, feeCalculator);
    }

    @Test
    void createOrder_onlineWithCodAmount_isRejected() {
        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("A-1", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, new BigDecimal("100")),
                OrderSource.DASHBOARD))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("codAmount");

        verifyNoInteractions(orderRepository, feeCalculator);
    }

    @Test
    void createOrder_duplicateReferenceForSameMerchant_throwsConflictAndSavesNothing() {
        when(orderRepository.existsByMerchantIdAndMerchantReference(7L, "SHOP-1001")).thenReturn(true);

        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("SHOP-1001", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.EXTERNAL_API))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("SHOP-1001");

        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(feeCalculator);
    }

    @Test
    void createOrder_raceOnReferenceConstraint_isTranslatedToDuplicate() {
        when(feeCalculator.calculateFee(any())).thenReturn(new BigDecimal("450.00"));
        when(orderRepository.nextOrderSequence()).thenReturn(43L);
        when(orderRepository.saveAndFlush(any(MerchantOrder.class))).thenThrow(
                new DataIntegrityViolationException("could not execute statement",
                        new RuntimeException(REFERENCE_CONSTRAINT_MESSAGE)));

        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("SHOP-1001", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.EXTERNAL_API))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    void createOrder_otherIntegrityViolation_isNotMaskedAsDuplicate() {
        when(feeCalculator.calculateFee(any())).thenReturn(new BigDecimal("450.00"));
        when(orderRepository.nextOrderSequence()).thenReturn(44L);
        when(orderRepository.saveAndFlush(any(MerchantOrder.class))).thenThrow(
                new DataIntegrityViolationException("could not execute statement",
                        new RuntimeException("violates check constraint \"ck_merchant_orders_weight\"")));

        assertThatThrownBy(() -> orderService.createOrder(7L,
                request("SHOP-1001", DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.DASHBOARD))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createOrder_invalidFeeFromCalculator_isRejected() {
        when(feeCalculator.calculateFee(any())).thenReturn(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> orderService.createOrder(7L,
                request(null, DeliveryType.NORMAL, null, PaymentMethod.ONLINE, null), OrderSource.DASHBOARD))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).saveAndFlush(any());
    }

    // ---------- read ----------

    @Test
    void getOrder_ownOrder_returnsIt() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(orderEntity(1L, 7L)));

        MerchantOrderResponse response = orderService.getOrder(7L, 1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.merchantId()).isEqualTo(7L);
    }

    @Test
    void getOrder_unknownId_throwsNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(7L, 99L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getOrder_anotherMerchantsOrder_throwsAccessDeniedWithoutDetails() {
        when(orderRepository.findById(2L)).thenReturn(Optional.of(orderEntity(2L, 8L)));

        assertThatThrownBy(() -> orderService.getOrder(7L, 2L))
                .isInstanceOf(OrderAccessDeniedException.class)
                .hasMessage("You do not have access to this order");
    }

    @Test
    void getOrders_queriesOnlyTheGivenMerchant() {
        when(orderRepository.findByMerchantId(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(orderEntity(1L, 7L))));

        Page<MerchantOrderResponse> page = orderService.getOrders(7L, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(orderRepository).findByMerchantId(eq(7L), any(Pageable.class));
    }

    @Test
    void getOrders_clampsPageAndSize() {
        when(orderRepository.findByMerchantId(eq(7L), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        orderService.getOrders(7L, -5, 1000);
        orderService.getOrders(7L, 3, 0);

        verify(orderRepository, org.mockito.Mockito.times(2)).findByMerchantId(eq(7L), pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isZero();
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(MerchantOrderService.MAX_PAGE_SIZE);
        assertThat(pageable.getAllValues().get(1).getPageNumber()).isEqualTo(3);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(MerchantOrderService.DEFAULT_PAGE_SIZE);
    }
}
