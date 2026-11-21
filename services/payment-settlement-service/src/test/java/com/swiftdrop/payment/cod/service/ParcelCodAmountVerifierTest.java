package com.swiftdrop.payment.cod.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

/** The verifier trusts Parcel & Pricing, not the event: any mismatch is rejected, an outage is retried. */
class ParcelCodAmountVerifierTest {

    private static final UUID BOOKING = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body;
    private final AtomicReference<String> keySeen = new AtomicReference<>();
    private final AtomicReference<String> pathSeen = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        body = parcel("COD", "SWD-2026-000321", MERCHANT.toString(), "SNP-ABC", "2500.00", "LKR");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            keySeen.set(exchange.getRequestHeaders().getFirst("X-Internal-Key"));
            pathSeen.set(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ParcelCodAmountVerifier verifier(String key) {
        var environment = new MockEnvironment()
            .withProperty("payment.pricing.base-url", "http://127.0.0.1:" + server.getAddress().getPort())
            .withProperty("payment.cod.parcel-internal-key", key);
        return new ParcelCodAmountVerifier(environment, JsonMapper.builder().build());
    }

    private static String parcel(String method, String tracking, String merchant, String snapshot, String amount,
                                 String currency) {
        return "{\"bookingId\":\"" + BOOKING + "\",\"trackingNumber\":\"" + tracking + "\",\"merchantId\":\""
            + merchant + "\",\"paymentMethod\":\"" + method + "\",\"codAmount\":" + amount
            + ",\"pricingSnapshotId\":\"" + snapshot + "\",\"currency\":\"" + currency + "\"}";
    }

    private static CodCollectedEvent.Data data(BigDecimal expected) {
        return new CodCollectedEvent.Data(BOOKING.toString(), "SWD-2026-000321", "SNP-ABC", MERCHANT,
            UUID.randomUUID(), UUID.randomUUID(), "COD", expected, new BigDecimal("2500.00"), "LKR",
            OffsetDateTime.now());
    }

    @Test
    void aMatchingEventPassesAndTheKeyIsSent() {
        assertDoesNotThrow(() -> verifier("shared-key").verify(data(new BigDecimal("2500.00"))));
        assertEquals("shared-key", keySeen.get());
        assertEquals("/api/v1/internal/parcels/" + BOOKING + "/cod-verification", pathSeen.get());
    }

    @Test
    void anAmountWrittenWithDifferentScaleStillMatches() {
        assertDoesNotThrow(() -> verifier("k").verify(data(new BigDecimal("2500"))));
    }

    @Test
    void anExpectedAmountThatDiffersFromTheBookingIsRejected() {
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2400.00"))));
    }

    @Test
    void aDifferentMerchantSnapshotTrackingCurrencyOrMethodIsRejected() {
        body = parcel("COD", "SWD-2026-000321", UUID.randomUUID().toString(), "SNP-ABC", "2500.00", "LKR");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        body = parcel("COD", "SWD-2026-000321", MERCHANT.toString(), "SNP-OTHER", "2500.00", "LKR");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        body = parcel("COD", "SWD-2026-999999", MERCHANT.toString(), "SNP-ABC", "2500.00", "LKR");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        body = parcel("COD", "SWD-2026-000321", MERCHANT.toString(), "SNP-ABC", "2500.00", "USD");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        body = parcel("ONLINE", "SWD-2026-000321", MERCHANT.toString(), "SNP-ABC", "2500.00", "LKR");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
    }

    @Test
    void aBookingWithNoCodAmountIsRejected() {
        body = body.replace("\"codAmount\":2500.00", "\"codAmount\":null");
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
    }

    @Test
    void anUnknownBookingIsRejectedForGood() {
        status = 404;
        body = "{}";
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
    }

    @Test
    void aRefusedKeyOrAnOutageIsRetriedNotDeadLettered() {
        status = 401;
        assertThrows(CodVerificationUnavailable.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        status = 503;
        assertThrows(CodVerificationUnavailable.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
        status = 200;
        body = "not json";
        assertThrows(CodVerificationUnavailable.class, () -> verifier("k").verify(data(new BigDecimal("2500.00"))));
    }

    @Test
    void aServiceThatIsDownIsRetried() {
        var verifier = verifier("k");
        server.stop(0);
        assertThrows(CodVerificationUnavailable.class, () -> verifier.verify(data(new BigDecimal("2500.00"))));
    }

    @Test
    void aBookingIdThatIsNotAnIdIsRejected() {
        var bad = new CodCollectedEvent.Data("not-a-uuid", "SWD-2026-000321", "SNP-ABC", MERCHANT, UUID.randomUUID(),
            UUID.randomUUID(), "COD", new BigDecimal("2500.00"), new BigDecimal("2500.00"), "LKR", OffsetDateTime.now());
        assertThrows(InvalidCodEvent.class, () -> verifier("k").verify(bad));
    }

    @Test
    void itRefusesToStartWithoutTheSharedKey() {
        assertThrows(IllegalStateException.class, () -> verifier(""));
    }
}
