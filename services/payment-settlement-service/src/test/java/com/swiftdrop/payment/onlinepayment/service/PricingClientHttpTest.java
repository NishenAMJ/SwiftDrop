package com.swiftdrop.payment.onlinepayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

/** Parcel-pricing lookups against a local stub HTTP server: status mapping, ownership and payability. */
class PricingClientHttpTest {
    private static final String CUSTOMER = "f2249d4c-a134-4bc0-b9ed-ffea97ad8791";
    private final Actor customer = new Actor(UUID.fromString(CUSTOMER), null, Set.of("CUSTOMER"), "customer-token");

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getRawPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get() == null ? new byte[0] : body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private PricingClient client() {
        var env = new MockEnvironment()
                .withProperty("payment.pricing.base-url", "http://127.0.0.1:" + server.getAddress().getPort());
        return new PricingClient(env, JsonMapper.builder().build(), new DefaultResourceLoader());
    }

    static String context(String bookingId, String customerId, boolean payable) {
        return """
            {"bookingId":"%s","channel":"DIRECT_WEBSITE","customerId":"%s","merchantId":null,"payable":%s,
             "pricingSnapshotId":"SNP-1234ABCD","amount":1500.00,"currency":"LKR",
             "payer":{"firstName":"Test","lastName":"Payer","email":"test@example.com","phone":"0770000000",
                      "address":"Address","city":"Colombo","country":"Sri Lanka"}}
            """.formatted(bookingId, customerId, payable);
    }

    private static void assertProblem(int expected, Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PaymentProblem.class,
                problem -> assertThat(problem.status).isEqualTo(expected));
    }

    @Test
    void forwardsTheCallersTokenAndEncodesTheBookingId() {
        body.set(context("SWD 2026/1", CUSTOMER, true));
        var result = client().lookup("SWD 2026/1", customer);
        assertThat(result.amount()).isEqualByComparingTo("1500.00");
        assertThat(authorization.get()).isEqualTo("Bearer customer-token");
        assertThat(path.get()).isEqualTo("/api/v1/bookings/SWD%202026%2F1/payment-context");
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHENTICATED", "403,FORBIDDEN", "404,NOT_FOUND", "409,CONFLICT"})
    void clientErrorsFromPricingKeepTheirMeaning(int upstream, String code) {
        status.set(upstream);
        assertThatThrownBy(() -> client().lookup("B-1", customer)).isInstanceOfSatisfying(PaymentProblem.class, problem -> {
            assertThat(problem.status).isEqualTo(upstream);
            assertThat(problem.code).isEqualTo(code);
        });
    }

    @Test
    void serverErrorsAndMalformedResponsesBecomeUpstreamUnavailable() {
        status.set(500);
        assertProblem(502, () -> client().lookup("B-1", customer));
        status.set(200);
        body.set("{not json");
        assertProblem(502, () -> client().lookup("B-1", customer));
        body.set(context("B-OTHER", CUSTOMER, true));
        assertProblem(502, () -> client().lookup("B-1", customer));
    }

    @Test
    void unreachablePricingServiceIsUpstreamUnavailable() {
        PricingClient client = client();
        server.stop(0);
        assertProblem(502, () -> client.lookup("B-1", customer));
    }

    @Test
    void anotherCustomersBookingIsForbidden() {
        body.set(context("B-1", UUID.randomUUID().toString(), true));
        assertProblem(403, () -> client().lookup("B-1", customer));
    }

    @Test
    void nonPayableBookingIsAConflict() {
        body.set(context("B-1", CUSTOMER, false));
        assertProblem(409, () -> client().lookup("B-1", customer));
    }

    @Test
    void localStubIsReadFromTheConfiguredResource(@TempDir Path dir) throws Exception {
        Path stub = dir.resolve("context.json");
        Files.writeString(stub, context("STUB-1", CUSTOMER, true));
        var env = new MockEnvironment()
                .withProperty("payment.pricing.stub-enabled", "true")
                .withProperty("payment.pricing.stub-resource", stub.toUri().toString());
        env.setActiveProfiles("local");
        var client = new PricingClient(env, JsonMapper.builder().build(), new DefaultResourceLoader());

        assertThat(client.lookup("STUB-1", customer).bookingId()).isEqualTo("STUB-1");
        assertProblem(404, () -> client.lookup("OTHER", customer));
    }

    @Test
    void payerDetailsAreValidated() {
        body.set(context("B-1", CUSTOMER, true).replace("test@example.com", "not-an-email"));
        assertProblem(502, () -> client().lookup("B-1", customer));
        body.set(context("B-1", CUSTOMER, true).replace("0770000000", "call-me"));
        assertProblem(502, () -> client().lookup("B-1", customer));
        body.set(context("B-1", CUSTOMER, true).replace("\"city\":\"Colombo\"", "\"city\":\" \""));
        assertProblem(502, () -> client().lookup("B-1", customer));
        body.set(context("B-1", CUSTOMER, true).replace("\"LKR\"", "\"USD\""));
        assertProblem(502, () -> client().lookup("B-1", customer));
        body.set(context("B-1", CUSTOMER, true).replace("1500.00", "-1"));
        assertProblem(502, () -> client().lookup("B-1", customer));
    }
}
