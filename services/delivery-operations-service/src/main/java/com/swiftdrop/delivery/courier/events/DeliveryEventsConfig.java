package com.swiftdrop.delivery.courier.events;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class DeliveryEventsConfig {

    /** delivery.* events for Tracking. Same name Tracking and Payment bind to. */
    public static final String DELIVERY_OPERATIONS_EXCHANGE = "swiftdrop.delivery-operations";

    /**
     * parcel.status-changed events for notification-webhook-service, which already binds this
     * exchange (see parcel-status-changed.schema.json). That service owns it; declaring it here with
     * identical settings only makes sure it exists if this service starts first.
     */
    public static final String NOTIFICATION_EXCHANGE = "swiftdrop.delivery";

    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange(DELIVERY_OPERATIONS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange notificationDeliveryExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }
}
