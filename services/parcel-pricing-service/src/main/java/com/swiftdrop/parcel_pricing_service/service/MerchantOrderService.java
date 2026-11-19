package com.swiftdrop.parcel_pricing_service.service;

import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderRequest;
import com.swiftdrop.parcel_pricing_service.dto.MerchantOrderResponse;
import com.swiftdrop.parcel_pricing_service.dto.OrderAddressDto;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class MerchantOrderService {

    private static final Logger log =
            LoggerFactory.getLogger(MerchantOrderService.class);

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final String ORDER_NUMBER_PREFIX = "MO-";

    private static final String REFERENCE_CONSTRAINT =
            "uq_merchant_orders_merchant_reference";

    private final MerchantOrderRepository orderRepository;
    private final DeliveryFeeCalculator feeCalculator;

    public MerchantOrderService(
            MerchantOrderRepository orderRepository,
            DeliveryFeeCalculator feeCalculator) {

        this.orderRepository = orderRepository;
        this.feeCalculator = feeCalculator;
    }

    @Transactional
    public MerchantOrderResponse createOrder(
            Long merchantId,
            MerchantOrderRequest request,
            OrderSource source) {

        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");

        String reference = trimToNull(request.merchantReference());

        validateBusinessRules(request, reference, source);

        if (reference != null &&
                orderRepository.existsByMerchantIdAndMerchantReference(
                        merchantId, reference)) {

            throw new DuplicateOrderException(
                    "An order with merchantReference '" +
                            reference + "' already exists");
        }

        OrderAddress pickup = toAddress(request.pickupAddress());
        OrderAddress delivery = toAddress(request.deliveryAddress());

        BigDecimal weight =
                request.weightKg().setScale(2, RoundingMode.HALF_UP);

        BigDecimal fee = feeCalculator.calculateFee(
                new DeliveryFeeRequest(
                        pickup,
                        delivery,
                        weight,
                        request.deliveryType()
                )
        );

        if (fee == null || fee.signum() < 0) {
            throw new IllegalStateException(
                    "Delivery fee calculation returned an invalid amount");
        }

        MerchantOrder order = new MerchantOrder();

        order.setOrderNumber(
                ORDER_NUMBER_PREFIX +
                        String.format(
                                "%08d",
                                orderRepository.nextOrderSequence()
                        )
        );

        order.setMerchantId(merchantId);
        order.setMerchantReference(reference);

        order.setSenderName(request.sender().name().trim());
        order.setSenderPhone(request.sender().phone().trim());
        order.setSenderEmail(trimToNull(request.sender().email()));

        order.setReceiverName(request.receiver().name().trim());
        order.setReceiverPhone(request.receiver().phone().trim());
        order.setReceiverEmail(trimToNull(request.receiver().email()));

        order.setPickupAddress(pickup);
        order.setDeliveryAddress(delivery);

        order.setParcelDescription(
                request.parcelDescription().trim()
        );

        order.setWeightKg(weight);
        order.setDeliveryType(request.deliveryType());

        order.setUrgentNote(
                request.deliveryType() == DeliveryType.URGENT
                        ? trimToNull(request.urgentNote())
                        : null
        );

        order.setPaymentMethod(request.paymentMethod());

        order.setCodAmount(
                request.paymentMethod() == PaymentMethod.COD
                        ? request.codAmount().setScale(
                                2, RoundingMode.HALF_UP)
                        : null
        );

        order.setDeliveryFee(
                fee.setScale(2, RoundingMode.HALF_UP)
        );

        order.setStatus(OrderStatus.CREATED);
        order.setSource(source);

        MerchantOrder saved = saveAndTranslate(order, reference);

        log.info(
                "Created merchant order id={} number={} merchantId={} source={}",
                saved.getId(),
                saved.getOrderNumber(),
                merchantId,
                source
        );

        return MerchantOrderResponse.from(saved);
    }

    public Page<MerchantOrderResponse> getOrders(
            Long merchantId,
            int page,
            int size) {

        Objects.requireNonNull(merchantId, "merchantId");

        int safePage = Math.max(page, 0);

        int safeSize = size < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(size, MAX_PAGE_SIZE);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return orderRepository
                .findByMerchantId(merchantId, pageable)
                .map(MerchantOrderResponse::from);
    }

    public MerchantOrderResponse getOrder(
            Long merchantId,
            Long orderId) {

        Objects.requireNonNull(merchantId, "merchantId");

        MerchantOrder order = orderRepository.findById(orderId)
                .orElseThrow(
                        () -> new OrderNotFoundException(orderId)
                );

        if (!merchantId.equals(order.getMerchantId())) {

            log.warn(
                    "Merchant {} attempted to access order {} owned by another merchant",
                    merchantId,
                    orderId
            );

            throw new OrderAccessDeniedException();
        }

        return MerchantOrderResponse.from(order);
    }

    private void validateBusinessRules(
            MerchantOrderRequest request,
            String reference,
            OrderSource source) {

        if (source == OrderSource.EXTERNAL_API && reference == null) {
            throw new InvalidOrderException(
                    "merchantReference is required for orders created through the external API"
            );
        }

        if (request.paymentMethod() == PaymentMethod.COD &&
                request.codAmount() == null) {

            throw new InvalidOrderException(
                    "codAmount is required when the payment method is COD"
            );
        }

        if (request.paymentMethod() == PaymentMethod.ONLINE &&
                request.codAmount() != null) {

            throw new InvalidOrderException(
                    "codAmount must not be set when the payment method is ONLINE"
            );
        }
    }

    private MerchantOrder saveAndTranslate(
            MerchantOrder order,
            String reference) {

        try {
            return orderRepository.saveAndFlush(order);

        } catch (DataIntegrityViolationException ex) {

            if (reference != null && isReferenceViolation(ex)) {

                throw new DuplicateOrderException(
                        "An order with merchantReference '" +
                                reference + "' already exists",
                        ex
                );
            }

            throw ex;
        }
    }

    private static boolean isReferenceViolation(
            DataIntegrityViolationException ex) {

        String message = ex.getMostSpecificCause().getMessage();

        return message != null &&
                message.contains(REFERENCE_CONSTRAINT);
    }

    private static OrderAddress toAddress(OrderAddressDto dto) {

        return new OrderAddress(
                dto.addressLine1().trim(),
                trimToNull(dto.addressLine2()),
                dto.city().trim(),
                trimToNull(dto.district()),
                trimToNull(dto.postalCode())
        );
    }

    private static String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();

        return trimmed.isEmpty() ? null : trimmed;
    }
}