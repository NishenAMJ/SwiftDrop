package com.swiftdrop.parcel_pricing_service;

import com.swiftdrop.parcel_pricing_service.client.BookingPaymentVerifier;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient;
import com.swiftdrop.parcel_pricing_service.client.IdentityClient.IdentityUser;
import com.swiftdrop.parcel_pricing_service.event.EventNames;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of Member 3's booking flows against real PostgreSQL and RabbitMQ.
 *
 * <p>Replaces the old contextLoads test. It proves that all Flyway migrations (V1-V11) apply to
 * an empty database, that Hibernate's schema validation passes, and that bookings, pricing,
 * events in both directions, payment confirmation and cancellation work together.
 * Identity and Payment HTTP calls are mocked; everything else is real.
 *
 * <p>Skipped automatically when Docker is not running.
 */
@SpringBootTest(properties = {
        // Never read the developer's local .env passwords in tests.
        "spring.config.import=",
        "swiftdrop.events.publisher.delay-ms=200",
        "swiftdrop.bookings.draft-expiry.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BookingFlowIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres;

    @ServiceConnection
    static final RabbitMQContainer rabbitmq;

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres = new PostgreSQLContainer("postgres:17").withInitScript("init-test-db.sql");
            postgres.start();
            rabbitmq = new RabbitMQContainer("rabbitmq:4-management");
            rabbitmq.start();
        } else {
            postgres = null;
            rabbitmq = null;
        }
    }

    private static final String BOOKED_QUEUE = "it.parcel-pricing-events";
    private static final String STATUS_QUEUE = "it.parcel-status-events";

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean IdentityClient identityClient;
    @MockitoBean BookingPaymentVerifier paymentVerifier;

    private final UUID customer = UUID.randomUUID();

    @BeforeAll
    static void requireDocker() {
        org.junit.jupiter.api.Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available; skipping integration test.");
    }

    @BeforeEach
    void setUp() {
        when(identityClient.currentUser(any())).thenReturn(Optional.of(
                new IdentityUser(customer, "nimal@example.com", "Nimal Perera", "+94771234567")));
        // Our own test queues, like Tracking and Notification would declare.
        TopicExchange parcelPricing = new TopicExchange(EventNames.PARCEL_PRICING_EXCHANGE, true, false);
        TopicExchange parcel = new TopicExchange(EventNames.PARCEL_EXCHANGE, true, false);
        amqpAdmin.declareExchange(parcelPricing);
        amqpAdmin.declareExchange(parcel);
        // RabbitMQ 4 no longer allows transient non-exclusive queues, so these are durable.
        Queue booked = new Queue(BOOKED_QUEUE, true);
        Queue statuses = new Queue(STATUS_QUEUE, true);
        amqpAdmin.declareQueue(booked);
        amqpAdmin.declareQueue(statuses);
        Binding bookedBinding = BindingBuilder.bind(booked).to(parcelPricing).with("parcel.*");
        Binding statusBinding = BindingBuilder.bind(statuses).to(parcel).with(EventNames.PARCEL_STATUS_CHANGED);
        amqpAdmin.declareBinding(bookedBinding);
        amqpAdmin.declareBinding(statusBinding);
        amqpAdmin.purgeQueue(BOOKED_QUEUE, false);
        amqpAdmin.purgeQueue(STATUS_QUEUE, false);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void allMigrationsApplyAndSeedDataIsPresent() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from parcel_pricing.flyway_schema_history where success", Integer.class);
        String latest = jdbc.queryForObject(
                "select max(cast(version as integer)) from parcel_pricing.flyway_schema_history", String.class);
        assertThat(applied).isEqualTo(11);
        assertThat(latest).isEqualTo("11");
        assertThat(jdbc.queryForObject("select count(*) from parcel_pricing.delivery_zones where status = 'ACTIVE'",
                Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void codBookingIsPricedConfirmedReceiptedAndAnnounced() throws Exception {
        JsonNode booking = createBooking("COD", "NORMAL", "KANDY");

        // Seeded STANDARD_DEFAULT rule and KANDY zone: 300 + 2 kg x 50 + 300 = 700.
        assertThat(booking.path("status").asString()).isEqualTo("BOOKED");
        assertThat(booking.path("estimatedFee").decimalValue()).isEqualByComparingTo("700.00");
        assertThat(booking.path("feeBreakdown").path("zoneCharge").decimalValue()).isEqualByComparingTo("300.00");
        String trackingNumber = booking.path("trackingNumber").asString();
        assertThat(trackingNumber).matches("^SWD-[0-9]{4}-[0-9]{6,}$");
        assertThat(booking.path("receiptNumber").asString()).isEqualTo("RCP-" + trackingNumber.substring(4));

        JsonNode receipt = getJson("/api/v1/bookings/" + booking.path("id").asString() + "/receipt");
        assertThat(receipt.path("customer").path("name").asString()).isEqualTo("Nimal Perera");
        assertThat(receipt.path("customer").path("email").asString()).isEqualTo("nimal@example.com");
        assertThat(receipt.path("bookingDate").asString()).isNotBlank();
        assertThat(receipt.path("paymentStatus").asString()).isEqualTo("PENDING");

        // parcel.booked for Tracking, with no personal data.
        JsonNode bookedEvent = awaitEvent(BOOKED_QUEUE, "parcel.booked", trackingNumber);
        assertThat(bookedEvent.path("producer").asString()).isEqualTo("parcel-pricing-service");
        assertThat(bookedEvent.path("data").path("deliveryStatus").asString()).isEqualTo("BOOKED");
        assertThat(bookedEvent.toString()).doesNotContain("12 Main Street", "0771234567", "nimal@example.com");

        // parcel.status-changed CREATED for Notification, with the customer to email.
        JsonNode statusEvent = awaitEvent(STATUS_QUEUE, "parcel.status-changed", trackingNumber);
        assertThat(statusEvent.path("data").path("status").asString()).isEqualTo("CREATED");
        assertThat(statusEvent.path("data").path("customerUserId").asString()).isEqualTo(customer.toString());

        // Only the owner can find it by tracking number.
        mvc.perform(get("/api/v1/bookings/track/" + trackingNumber).with(as(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlineBookingIsConfirmedByThePaymentSucceededEvent() throws Exception {
        JsonNode draft = createBooking("ONLINE", "URGENT", "COLOMBO");
        String id = draft.path("id").asString();
        assertThat(draft.path("status").asString()).isEqualTo("CHECKOUT_DRAFT");
        assertThat(draft.path("receiptNumber").isMissingNode() || draft.path("receiptNumber").isNull()).isTrue();
        assertThat(myBookingIds()).doesNotContain(id);

        // Payment reads the amount it will charge.
        JsonNode context = getJson("/api/v1/bookings/" + id + "/payment-context");
        assertThat(context.path("payable").asBoolean()).isTrue();
        assertThat(context.path("payer").path("email").asString()).isEqualTo("nimal@example.com");

        publish(EventNames.PAYMENT_EXCHANGE, "payment.succeeded", "payment-settlement-service", """
                {"paymentId":"%s","bookingId":"%s","amount":%s,"currency":"LKR","status":"SUCCESS","refundedAmount":0}
                """.formatted(UUID.randomUUID(), id, draft.path("estimatedFee").decimalValue().toPlainString()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            JsonNode booking = getJson("/api/v1/bookings/" + id);
            assertThat(booking.path("status").asString()).isEqualTo("BOOKED");
            assertThat(booking.path("paymentStatus").asString()).isEqualTo("PAID");
            assertThat(booking.path("receiptNumber").asString()).startsWith("RCP-");
        });
        assertThat(myBookingIds()).contains(id);
        awaitEvent(BOOKED_QUEUE, "parcel.booked", draft.path("trackingNumber").asString());
    }

    @Test
    void deliveryEventsUpdateTheStatusAndCloseCancellation() throws Exception {
        JsonNode booking = createBooking("COD", "NORMAL", "GAMPAHA");
        String id = booking.path("id").asString();
        String trackingNumber = booking.path("trackingNumber").asString();

        publish(EventNames.DELIVERY_EXCHANGE, "delivery.assigned", "delivery-operations-service", """
                {"parcelId":"%s","trackingNumber":"%s","deliveryStatus":"ASSIGNED","publicDescription":"Courier scheduled",
                 "assignmentId":"%s","courierId":"%s"}
                """.formatted(id, trackingNumber, UUID.randomUUID(), UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getJson("/api/v1/bookings/" + id).path("status").asString()).isEqualTo("ASSIGNED"));

        // A courier is scheduled, so the customer can no longer cancel.
        mvc.perform(post("/api/v1/bookings/" + id + "/cancel").with(as(customer)))
                .andExpect(status().isConflict());
    }

    @Test
    void bookedParcelCanBeCancelledAndIsAnnounced() throws Exception {
        JsonNode booking = createBooking("COD", "NORMAL", "COLOMBO");
        String id = booking.path("id").asString();

        String body = mvc.perform(post("/api/v1/bookings/" + id + "/cancel").with(as(customer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        assertThat(result.path("bookingStatus").asString()).isEqualTo("CANCELLED");
        assertThat(result.path("cancellationStatus").asString()).isEqualTo("COMPLETED");

        JsonNode cancelled = awaitEvent(BOOKED_QUEUE, "parcel.cancelled", booking.path("trackingNumber").asString());
        assertThat(cancelled.path("data").path("paymentMethod").asString()).isEqualTo("COD");
        assertThat(cancelled.path("data").path("refundAmount").decimalValue()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------ helpers

    private RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()).claim("roles", List.of("CUSTOMER")))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private JsonNode createBooking(String paymentMethod, String urgency, String deliveryZone) throws Exception {
        String request = """
                {"description":"Documents","weightKg":2.00,"urgency":"%s","paymentMethod":"%s",
                 "pickupAddress":{"contactName":"Nimal Perera","contactPhone":"0771234567",
                                  "streetAddress":"12 Main Street","city":"Colombo","zoneCode":"COLOMBO"},
                 "deliveryAddress":{"contactName":"Kamala Silva","contactPhone":"0777654321",
                                    "streetAddress":"45 Lake Road","city":"Kandy","zoneCode":"%s"}}
                """.formatted(urgency, paymentMethod, deliveryZone);
        String body = mvc.perform(post("/api/v1/bookings").with(as(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode getJson(String path) throws Exception {
        String body = mvc.perform(get(path).with(as(customer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private List<String> myBookingIds() throws Exception {
        List<String> ids = new ArrayList<>();
        getJson("/api/v1/bookings/my").forEach(b -> ids.add(b.path("id").asString()));
        return ids;
    }

    /** Publishes an event in the shared envelope, as another service would. */
    private void publish(String exchange, String eventType, String producer, String data) {
        String envelope = """
                {"eventId":"%s","eventType":"%s","version":1,"occurredAt":"%s","producer":"%s","data":%s}
                """.formatted(UUID.randomUUID(), eventType, Instant.now(), producer, data.strip());
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.send(exchange, eventType, new Message(envelope.getBytes(StandardCharsets.UTF_8), props));
    }

    /** Waits for the outbox worker to publish an event for this tracking number. */
    private JsonNode awaitEvent(String queue, String eventType, String trackingNumber) {
        JsonNode[] found = new JsonNode[1];
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message message = rabbit.receive(queue, 500);
            if (message == null) return false;
            JsonNode event = json.readTree(message.getBody());
            if (eventType.equals(event.path("eventType").asString())
                    && trackingNumber.equals(event.path("data").path("trackingNumber").asString())) {
                assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.path("eventId").asString());
                found[0] = event;
                return true;
            }
            return false;
        });
        return found[0];
    }
}
