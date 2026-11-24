package com.swiftdrop.delivery.courier.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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

    /** Parcel & Pricing's exchange, which announces parcel.booked and parcel.cancelled. */
    public static final String PARCEL_PRICING_EXCHANGE = "swiftdrop.parcel-pricing";
    public static final String PARCEL_CANCELLED_QUEUE = "delivery-operations-service.parcel-cancelled";
    static final String DEAD_LETTER_EXCHANGE = "swiftdrop.delivery-operations.dlx";
    static final String PARCEL_CANCELLED_DEAD_LETTER_QUEUE = PARCEL_CANCELLED_QUEUE + ".dead-letter";

    @Bean
    TopicExchange parcelPricingExchange() {
        return new TopicExchange(PARCEL_PRICING_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deliveryDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue parcelCancelledQueue() {
        return QueueBuilder.durable(PARCEL_CANCELLED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PARCEL_CANCELLED_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue parcelCancelledDeadLetterQueue() {
        return QueueBuilder.durable(PARCEL_CANCELLED_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding parcelCancelledBinding(Queue parcelCancelledQueue, TopicExchange parcelPricingExchange) {
        return BindingBuilder.bind(parcelCancelledQueue).to(parcelPricingExchange).with("parcel.cancelled");
    }

    @Bean
    Binding parcelCancelledDeadLetterBinding(Queue parcelCancelledDeadLetterQueue,
                                             DirectExchange deliveryDeadLetterExchange) {
        return BindingBuilder.bind(parcelCancelledDeadLetterQueue).to(deliveryDeadLetterExchange)
                .with(PARCEL_CANCELLED_DEAD_LETTER_QUEUE);
    }

    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange(DELIVERY_OPERATIONS_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange notificationDeliveryExchange() {
        return new TopicExchange(NOTIFICATION_EXCHANGE, true, false);
    }
}
