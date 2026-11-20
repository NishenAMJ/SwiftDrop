package com.swiftdrop.communication.webhook;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.communication.common.PageResponse;
import com.swiftdrop.communication.webhook.dto.CreateSubscriptionRequest;
import com.swiftdrop.communication.webhook.dto.UpdateSubscriptionRequest;
import com.swiftdrop.communication.webhook.dto.WebhookDeliveryResponse;
import com.swiftdrop.communication.webhook.dto.WebhookSubscriptionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/webhooks")
@Validated
public class WebhookController {

    private final WebhookSubscriptionService subscriptionService;
    private final MerchantSecurity merchantSecurity;

    public WebhookController(WebhookSubscriptionService subscriptionService,
                             MerchantSecurity merchantSecurity) {
        this.subscriptionService = subscriptionService;
        this.merchantSecurity = merchantSecurity;
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<WebhookSubscriptionResponse> createSubscription(
            @Valid @RequestBody CreateSubscriptionRequest request,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        WebhookSubscriptionResponse response = subscriptionService.createSubscription(merchantId, request);
        return ResponseEntity
                .created(URI.create("/api/v1/webhooks/subscriptions/" + response.getId()))
                .body(response);
    }

    @GetMapping("/subscriptions")
    @PreAuthorize("hasRole('MERCHANT')")
    public PageResponse<WebhookSubscriptionResponse> listSubscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WebhookSubscriptionResponse> result = subscriptionService.listSubscriptions(merchantId, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/subscriptions/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookSubscriptionResponse getSubscription(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        return subscriptionService.getSubscription(merchantId, id);
    }

    @PatchMapping("/subscriptions/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookSubscriptionResponse updateSubscription(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        return subscriptionService.updateSubscription(merchantId, id, request);
    }

    @DeleteMapping("/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MERCHANT')")
    public void deleteSubscription(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        subscriptionService.deleteSubscription(merchantId, id);
    }

    @PostMapping("/subscriptions/{id}/rotate-secret")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookSubscriptionResponse rotateSecret(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        return subscriptionService.rotateSecret(merchantId, id);
    }

    @PostMapping("/subscriptions/{id}/test")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<WebhookDeliveryResponse> testSubscription(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        WebhookDeliveryResponse response = subscriptionService.testSubscription(merchantId, id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/subscriptions/{id}/deliveries")
    @PreAuthorize("hasRole('MERCHANT')")
    public PageResponse<WebhookDeliveryResponse> listDeliveries(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WebhookDeliveryResponse> result = subscriptionService.listDeliveries(merchantId, id, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/deliveries/{deliveryId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public WebhookDeliveryResponse getDelivery(
            @PathVariable UUID deliveryId,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        return subscriptionService.getDelivery(merchantId, deliveryId);
    }

    @PostMapping("/deliveries/{deliveryId}/retry")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<WebhookDeliveryResponse> retryDelivery(
            @PathVariable UUID deliveryId,
            Authentication authentication) {
        UUID merchantId = merchantSecurity.requireMerchantId(authentication);
        WebhookDeliveryResponse response = subscriptionService.retryDelivery(merchantId, deliveryId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping(value = "/deliveries", params = "status=FAILED")
    @PreAuthorize("hasRole('DISPATCHER')")
    public PageResponse<WebhookDeliveryResponse> listFailedDeliveries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<WebhookDeliveryResponse> result = subscriptionService.listFailedDeliveriesForDispatcher(pageable);
        return PageResponse.from(result);
    }
}
