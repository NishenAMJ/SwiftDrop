package com.swiftdrop.communication.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.communication.common.ApiException;
import com.swiftdrop.communication.config.WebhookProperties;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Dispatcher guard paths and response handling, using a local stub merchant endpoint. */
class WebhookDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final WebhookDeliveryRepository deliveries = mock(WebhookDeliveryRepository.class);
    private final WebhookSubscriptionRepository subscriptions = mock(WebhookSubscriptionRepository.class);
    private final WebhookSecretCrypto crypto = mock(WebhookSecretCrypto.class);
    private final UrlSafetyValidator urls = mock(UrlSafetyValidator.class);
    private final WebhookProperties properties = new WebhookProperties();
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private WebhookDispatcher dispatcher;

    private HttpServer merchant;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> signature = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(crypto.decrypt(any())).thenReturn("raw-secret");
        properties.setRetryDelays(List.of(Duration.ofSeconds(30), Duration.ofMinutes(5)));
        dispatcher = new WebhookDispatcher(deliveries, subscriptions, crypto, new WebhookSigner(), urls, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), tx);

        merchant = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        merchant.createContext("/hook", exchange -> {
            hits.incrementAndGet();
            signature.set(exchange.getRequestHeaders().getFirst("X-SwiftDrop-Signature"));
            exchange.getRequestBody().readAllBytes();
            if (status.get() == 302) exchange.getResponseHeaders().set("Location", "http://169.254.169.254/latest/meta-data");
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        merchant.start();
    }

    @AfterEach
    void tearDown() {
        merchant.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + merchant.getAddress().getPort() + "/hook";
    }

    private WebhookDelivery pending(UUID subscriptionId, int attempts) {
        var delivery = new WebhookDelivery(UUID.randomUUID(), subscriptionId, UUID.randomUUID(), "parcel.status-changed",
                "{\"hello\":\"merchant\"}", WebhookDeliveryStatus.PENDING, attempts, NOW, NOW);
        when(deliveries.findById(delivery.getDeliveryId())).thenReturn(Optional.of(delivery));
        return delivery;
    }

    private WebhookSubscription subscription(boolean active) {
        var subscription = new WebhookSubscription(UUID.randomUUID(), UUID.randomUUID(), url(), "parcel.status-changed",
                "cipher", active, active ? null : "MERCHANT_SUSPENDED", NOW, NOW);
        when(subscriptions.findById(subscription.getSubscriptionId())).thenReturn(Optional.of(subscription));
        return subscription;
    }

    @Test
    void unknownOrAlreadyFinishedDeliveriesAreSkipped() {
        UUID missing = UUID.randomUUID();
        when(deliveries.findById(missing)).thenReturn(Optional.empty());
        dispatcher.processSingleDelivery(missing);

        var delivered = pending(UUID.randomUUID(), 1);
        delivered.setStatus(WebhookDeliveryStatus.DELIVERED);
        dispatcher.processSingleDelivery(delivered.getDeliveryId());

        verify(deliveries, never()).save(any());
        assertThat(hits.get()).isZero();
    }

    @Test
    void deliveryForADeletedSubscriptionFailsPermanently() {
        var delivery = pending(UUID.randomUUID(), 0);
        when(subscriptions.findById(delivery.getSubscriptionId())).thenReturn(Optional.empty());
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).isEqualTo("Subscription not found");
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(hits.get()).isZero();
    }

    @Test
    void pausedSubscriptionPostponesWithoutUsingAnAttempt() {
        var subscription = subscription(false);
        var delivery = pending(subscription.getSubscriptionId(), 0);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
        assertThat(hits.get()).isZero();
    }

    @Test
    void urlThatBecameUnsafeIsNeverCalled() {
        var subscription = subscription(true);
        var delivery = pending(subscription.getSubscriptionId(), 0);
        doThrow(ApiException.validation("url", "Private or loopback addresses are not allowed."))
                .when(urls).validate(subscription.getUrl());
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getLastError()).startsWith("URL validation failed");
        assertThat(hits.get()).isZero();
    }

    @Test
    void successfulDeliveryIsSignedAndRecorded() {
        var subscription = subscription(true);
        var delivery = pending(subscription.getSubscriptionId(), 0);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(NOW);
        assertThat(delivery.getLastStatusCode()).isEqualTo(200);
        assertThat(new WebhookSigner().verify("raw-secret", signature.get(), "{\"hello\":\"merchant\"}", 0)).isTrue();
    }

    @Test
    void redirectsAreNotFollowedAndCountAsAFailedAttempt() {
        status.set(302);
        var subscription = subscription(true);
        var delivery = pending(subscription.getSubscriptionId(), 0);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(hits.get()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
    }

    @Test
    void serverErrorStoresOnlyTheStatusNotTheMerchantsResponseBody() {
        status.set(503);
        var subscription = subscription(true);
        var delivery = pending(subscription.getSubscriptionId(), 0);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getLastStatusCode()).isEqualTo(503);
        assertThat(delivery.getLastError()).isEqualTo("HTTP 503");
    }

    @Test
    void lastAllowedAttemptFailsPermanently() {
        status.set(500);
        var subscription = subscription(true);
        var delivery = pending(subscription.getSubscriptionId(), 2);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getAttemptCount()).isEqualTo(3);
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void unreachableMerchantRecordsATruncatedTransportError() {
        var subscription = subscription(true);
        subscription.setUrl("http://127.0.0.1:1/hook");
        var delivery = pending(subscription.getSubscriptionId(), 0);
        dispatcher.processSingleDelivery(delivery.getDeliveryId());
        assertThat(delivery.getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
        assertThat(delivery.getLastStatusCode()).isNull();
        assertThat(delivery.getLastError()).isNotBlank().hasSizeLessThanOrEqualTo(WebhookDispatcher.MAX_ERROR_LENGTH);
    }

    @Test
    void pollingContinuesAfterOneDeliveryThrows() {
        var subscription = subscription(true);
        var good = pending(subscription.getSubscriptionId(), 0);
        UUID broken = UUID.randomUUID();
        when(deliveries.findById(broken)).thenThrow(new IllegalStateException("row lock lost"));
        var bad = new WebhookDelivery(broken, subscription.getSubscriptionId(), UUID.randomUUID(), "x", "{}",
                WebhookDeliveryStatus.PENDING, 0, NOW, NOW);
        when(deliveries.findDuePendingDeliveriesForUpdate(NOW, 20)).thenReturn(List.of(bad, good));

        dispatcher.pollAndDispatch();

        assertThat(good.getStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
    }
}
