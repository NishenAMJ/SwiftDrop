package com.swiftdrop.identity.event;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The exchange this service publishes to. Queues belong to the consumers, which bind them with
 * routing-key patterns such as {@code user.*} or {@code merchant.*}.
 */
@Configuration
public class EventExchangeConfig {

    @Bean
    TopicExchange identityExchange() {
        return new TopicExchange(OutboxPublisher.EXCHANGE, true, false);
    }
}
