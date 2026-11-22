package com.swiftdrop.payment.onlinepayment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Payer;
import com.swiftdrop.payment.onlinepayment.entity.Payment;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

/**
 * PayHere refund, retrieval and callback-signature behaviour against a local stub server.
 * The provider never reaches the real PayHere API from these tests.
 */
class PayHereProviderStubServerTest {
    private static final UUID ORDER = UUID.fromString("f2249d4c-a134-4bc0-b9ed-ffea97ad8791");

    private HttpServer server;
    private final AtomicInteger tokenStatus = new AtomicInteger(200);
    private final AtomicReference<String> tokenBody = new AtomicReference<>("{\"access_token\":\"stub-token\"}");
    private final AtomicInteger apiStatus = new AtomicInteger(200);
    private final AtomicReference<String> apiBody = new AtomicReference<>("{}");
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicInteger refundCalls = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/merchant/v1/oauth/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, tokenStatus.get(), tokenBody.get());
        });
        server.createContext("/merchant/v1/payment/refund", exchange -> {
            refundCalls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            respond(exchange, apiStatus.get(), apiBody.get());
        });
        server.createContext("/merchant/v1/payment/search", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            respond(exchange, apiStatus.get(), apiBody.get());
        });
        server.start();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PayHereProvider provider() {
        var env = new MockEnvironment()
                .withProperty("payment.payhere.base-url", "http://127.0.0.1:" + server.getAddress().getPort())
                .withProperty("payment.payhere.app-id", "stub-app")
                .withProperty("payment.payhere.app-secret", "stub-secret")
                .withProperty("payment.payhere.merchant-id", "123")
                .withProperty("payment.payhere.merchant-secret", "test-secret");
        return new PayHereProvider(env, JsonMapper.builder().build());
    }

    // ---------- refunds ----------

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void rejectedRefundIsReportedAsFailed(int status) {
        apiBody.set("{\"status\":" + status + ",\"msg\":\"Refund rejected\"}");
        var result = provider().refund("320027150501", new BigDecimal("10.00"), "Test");
        assertThat(result.outcome()).isEqualTo("FAILED");
        assertThat(result.reference()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":1,\"data\":\"not-a-number\"}", "{\"status\":2}", "{}"})
    void ambiguousRefundResponsesAreUnknownNeverSuccess(String body) {
        apiBody.set(body);
        assertThat(provider().refund("320027150501", new BigDecimal("10.00"), "Test").outcome()).isEqualTo("UNKNOWN");
    }

    @Test
    void transportFailuresDuringRefundAreUnknownBecauseMoneyMayHaveMoved() {
        apiStatus.set(500);
        assertThat(provider().refund("320027150501", BigDecimal.TEN, "Test").outcome()).isEqualTo("UNKNOWN");
        assertThat(refundCalls.get()).isEqualTo(1);

        apiStatus.set(200);
        apiBody.set("<html>gateway error</html>");
        assertThat(provider().refund("320027150501", BigDecimal.TEN, "Test").outcome()).isEqualTo("UNKNOWN");
        assertThat(refundCalls.get()).as("no automatic retry").isEqualTo(2);
    }

    @Test
    void refundIsNotSentWithoutAnAccessToken() {
        tokenBody.set("{\"access_token\":\"\"}");
        assertThat(provider().refund("320027150501", BigDecimal.TEN, "Test").outcome()).isEqualTo("UNKNOWN");
        tokenStatus.set(401);
        assertThat(provider().refund("320027150501", BigDecimal.TEN, "Test").outcome()).isEqualTo("UNKNOWN");
        assertThat(refundCalls.get()).isZero();
    }

    @Test
    void missingProviderConfigurationIsReportedAsNotConfigured() {
        var unconfigured = new PayHereProvider(new MockEnvironment(), JsonMapper.builder().build());
        assertThatThrownBy(() -> unconfigured.retrieve(ORDER)).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.code).isEqualTo("PROVIDER_NOT_CONFIGURED"));
        Payment payment = new Payment();
        payment.setId(ORDER);
        payment.setAmount(BigDecimal.TEN);
        payment.setCurrency("LKR");
        assertThatThrownBy(() -> unconfigured.checkout(payment,
                new Payer("A", "B", "a@b.lk", "0770000000", "Addr", "Colombo", "Sri Lanka")))
                .isInstanceOfSatisfying(PaymentProblem.class, problem -> assertThat(problem.status).isEqualTo(503));
    }

    // ---------- retrieval ----------

    @Test
    void retrievalParsesEveryReturnedPaymentForTheOrder() {
        apiBody.set("""
            {"status":1,"data":[
              {"payment_id":"320025","order_id":"%1$s","amount":"1000.00","currency":"LKR","status":"RECEIVED"},
              {"payment_id":"320026","order_id":"%1$s","amount":"1000.00","currency":"LKR","status":"CHARGEBACKED"}]}
            """.formatted(ORDER));
        var payments = provider().retrieve(ORDER);
        assertThat(query.get()).isEqualTo("order_id=" + ORDER);
        assertThat(payments).hasSize(2);
        assertThat(payments.getFirst().reference()).isEqualTo("320025");
        assertThat(payments.getFirst().amount()).isEqualByComparingTo("1000");
        assertThat(payments.get(1).status()).isEqualTo("CHARGEBACKED");
    }

    @Test
    void noPaymentFoundIsAnEmptyResultNotAnError() {
        apiBody.set("{\"status\":-1,\"msg\":\"No payments found\"}");
        assertThat(provider().retrieve(ORDER)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":1,\"data\":{}}", "{\"status\":1,\"data\":[{\"amount\":\"not-money\"}]}", "not json"})
    void malformedRetrievalResponsesAreUpstreamUnavailable(String body) {
        apiBody.set(body);
        assertThatThrownBy(() -> provider().retrieve(ORDER)).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(502));
    }

    @Test
    void retrievalHttpErrorIsUpstreamUnavailable() {
        apiStatus.set(503);
        assertThatThrownBy(() -> provider().retrieve(ORDER)).isInstanceOf(PaymentProblem.class);
    }

    // ---------- callback signatures ----------

    private Map<String, String> callback(String status, String amount, String reference) {
        Map<String, String> fields = new HashMap<>(Map.of("merchant_id", "123", "order_id", ORDER.toString(),
                "payhere_amount", amount, "payhere_currency", "LKR", "status_code", status));
        if (reference != null) fields.put("payment_id", reference);
        String secret = PayHereProvider.digest("MD5", "test-secret").toUpperCase(Locale.ROOT);
        fields.put("md5sig", PayHereProvider.digest("MD5", "123" + ORDER + amount + "LKR" + status + secret).toUpperCase(Locale.ROOT));
        return fields;
    }

    @Test
    void lowercaseSignatureIsAccepted() {
        var fields = callback("2", "1000.00", "320025");
        fields.put("md5sig", fields.get("md5sig").toLowerCase(Locale.ROOT));
        assertThat(provider().verify(fields).status()).isEqualTo(2);
    }

    @Test
    void tamperedSignatureIsRejected() {
        var fields = callback("2", "1000.00", "320025");
        fields.put("md5sig", "0".repeat(32));
        assertThatThrownBy(() -> provider().verify(fields)).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.code).isEqualTo("INVALID_CALLBACK"));
    }

    @Test
    void unknownStatusCodesAndNonNumericStatusesAreRejected() {
        assertThatThrownBy(() -> provider().verify(callback("5", "1000.00", "320025"))).isInstanceOf(PaymentProblem.class);
        assertThatThrownBy(() -> provider().verify(callback("ok", "1000.00", "320025"))).isInstanceOf(PaymentProblem.class);
    }

    @Test
    void zeroAndOverPreciseAmountsAreRejected() {
        assertThatThrownBy(() -> provider().verify(callback("2", "0.00", "320025"))).isInstanceOf(PaymentProblem.class);
        assertThatThrownBy(() -> provider().verify(callback("2", "10.005", "320025"))).isInstanceOf(PaymentProblem.class);
    }

    @Test
    void chargebackRequiresANumericProviderReferenceButFailureDoesNot() {
        assertThatThrownBy(() -> provider().verify(callback("-3", "1000.00", "abc"))).isInstanceOf(PaymentProblem.class);
        var failed = provider().verify(callback("-2", "1000.00", null));
        assertThat(failed.reference()).isNull();
        assertThat(failed.status()).isEqualTo(-2);
    }

    @Test
    void differentProviderReferencesProduceDifferentFingerprints() {
        var first = provider().verify(callback("2", "1000.00", "320025"));
        var second = provider().verify(callback("2", "1000.00", "320026"));
        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }
}
