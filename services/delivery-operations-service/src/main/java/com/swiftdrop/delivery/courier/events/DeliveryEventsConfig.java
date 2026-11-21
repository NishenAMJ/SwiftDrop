package com.swiftdrop.delivery.courier.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class DeliveryEventsConfig {

    /** Same name Tracking and Payment bind to; declaring it again with identical settings is harmless. */
    public static final String DELIVERY_EXCHANGE = "swiftdrop.delivery-operations";

    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange(DELIVERY_EXCHANGE, true, false);
    }
}
