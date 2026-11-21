package com.swiftdrop.delivery.courier.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OutboxPublisherTest {

    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    private RabbitTemplate rabbit;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_events");
        rabbit = mock(RabbitTemplate.class);
        publisher = new OutboxPublisher(jdbc, transaction, rabbit, "outbox_events");
    }

    @Test
    void confirmedEventsAreSentToTheDeliveryExchangeAndMarkedPublished() {
        var id = UUID.randomUUID();
        outbox.save(new OutboxEvent(id, "swiftdrop.delivery-operations", "delivery.delivered",
                "{\"eventType\":\"delivery.delivered\"}", Instant.now()));
        brokerAnswers(true);

        publisher.publishPending();

        var message = ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("swiftdrop.delivery-operations"), eq("delivery.delivered"), message.capture(),
                any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"eventType\":\"delivery.delivered\"}");
        assertThat(message.getValue().getMessageProperties().getMessageId()).isEqualTo(id.toString());
        assertThat(outbox.findById(id).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void anUnconfirmedEventIsKeptAndRetriedLater() {
        var id = UUID.randomUUID();
        outbox.save(new OutboxEvent(id, "swiftdrop.delivery-operations", "delivery.failed", "{}", Instant.now()));
        brokerAnswers(false);

        publisher.publishPending();

        var event = outbox.findById(id).orElseThrow();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());

        // Not due yet, so a second pass leaves it alone.
        publisher.publishPending();
        assertThat(outbox.findById(id).orElseThrow().getAttempts()).isEqualTo(1);
    }

    @Test
    void eachEventGoesToTheExchangeItWasWrittenFor() {
        outbox.save(new OutboxEvent(UUID.randomUUID(), "swiftdrop.delivery", "parcel.status-changed", "{}",
                Instant.now()));
        brokerAnswers(true);

        publisher.publishPending();

        verify(rabbit).send(eq("swiftdrop.delivery"), eq("parcel.status-changed"), any(Message.class),
                any(CorrelationData.class));
    }

    @Test
    void publishedEventsAreNotSentAgain() {
        outbox.save(new OutboxEvent(UUID.randomUUID(), "swiftdrop.delivery-operations", "delivery.assigned", "{}",
                Instant.now()));
        brokerAnswers(true);
        publisher.publishPending();

        publisher.publishPending();

        verify(rabbit, org.mockito.Mockito.times(1)).send(any(String.class), any(String.class), any(Message.class),
                any(CorrelationData.class));
        verify(rabbit, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    private void brokerAnswers(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nacked"));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }
}
