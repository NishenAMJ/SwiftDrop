package com.swiftdrop.identity.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.swiftdrop.identity.ApiTestSupport;
import com.swiftdrop.identity.config.IdentityProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Identity events reach RabbitMQ. Needs a running broker, like the CI job and local Compose provide.
 */
class OutboxPublisherTests extends ApiTestSupport {

    @Autowired
    private OutboxPublisher publisher;
    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private AmqpAdmin admin;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private IdentityProperties properties;
    @Autowired
    private JsonMapper jsonMapper;

    private Queue queue;

    @BeforeEach
    void listenForUserEvents() {
        // Events left by earlier tests (the background publisher is off) go out first.
        while (publisher.publishPending().published() > 0) {
            // keep going
        }
        queue = new AnonymousQueue();
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(OutboxPublisher.EXCHANGE)).with("user.*"));
    }

    @AfterEach
    void removeQueue() {
        admin.deleteQueue(queue.getName());
    }

    @Test
    void registrationEventIsPublishedWithTheContractProperties() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        OutboxEvent recorded = waitingEventFor(email);

        OutboxPublisher.Result result = publisher.publishPending();

        assertThat(result.failed()).isFalse();
        assertThat(repository.findById(recorded.getId()).orElseThrow().getPublishedAt()).isNotNull();
        Message message = rabbit.receive(queue.getName(), 5000);
        assertThat(message).as("message on the swiftdrop.identity exchange").isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("user.registered");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(recorded.getId().toString());
        assertThat(message.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        JsonNode envelope = jsonMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(envelope.get("eventId").asString()).isEqualTo(recorded.getId().toString());
        assertThat(envelope.get("eventType").asString()).isEqualTo("user.registered");
        assertThat(envelope.get("producer").asString()).isEqualTo("identity-merchant-service");
        assertThat(envelope.get("data").get("email").asString()).isEqualTo(email);
    }

    @Test
    void eventsWaitWhileRabbitMqIsUnreachableAndAreSentLater() throws Exception {
        String email = uniqueEmail();
        register(email, PASSWORD).andExpect(status().isCreated());
        OutboxEvent recorded = waitingEventFor(email);

        CachingConnectionFactory unreachable = new CachingConnectionFactory("localhost", 1);
        unreachable.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        unreachable.setConnectionTimeout(1000);
        try {
            OutboxPublisher offline = new OutboxPublisher(repository, new RabbitTemplate(unreachable),
                    transaction, Clock.systemUTC(), properties);

            OutboxPublisher.Result result = offline.publishPending();

            assertThat(result.failed()).isTrue();
            assertThat(result.published()).isZero();
            assertThat(repository.findById(recorded.getId()).orElseThrow().getPublishedAt()).isNull();
        } finally {
            unreachable.destroy();
        }

        assertThat(publisher.publishPending().failed()).isFalse();
        assertThat(repository.findById(recorded.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(rabbit.receive(queue.getName(), 5000)).isNotNull();
    }

    private OutboxEvent waitingEventFor(String email) {
        OutboxEvent event = repository.findAll().stream()
                .filter(e -> e.getPublishedAt() == null && e.getPayload().contains(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no waiting outbox event for " + email));
        assertThat(event.getEventType()).isEqualTo("user.registered");
        return event;
    }
}
