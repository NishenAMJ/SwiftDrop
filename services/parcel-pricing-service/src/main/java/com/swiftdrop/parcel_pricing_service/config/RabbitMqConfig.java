package com.swiftdrop.parcel_pricing_service.config;

import com.swiftdrop.parcel_pricing_service.event.EventNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Exchanges this service publishes to and the queues it owns.
 *
 * <p>Exchange settings (durable topic, not auto-delete) match what Tracking, Notification,
 * Payment and Delivery Operations declare, so whichever service starts first creates them.
 * Each service has its own queue, so every service gets its own copy of an event.
 * A message that cannot be processed goes to this service's dead-letter queue instead of looping.
 */
@Configuration
@EnableScheduling
public class RabbitMqConfig {

    // Exchanges we publish to
    @Bean
    TopicExchange parcelPricingExchange() {
        return new TopicExchange(EventNames.PARCEL_PRICING_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange parcelExchange() {
        return new TopicExchange(EventNames.PARCEL_EXCHANGE, true, false);
    }

    // Exchanges we consume from
    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange(EventNames.DELIVERY_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange paymentExchange() {
        return new TopicExchange(EventNames.PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange parcelPricingDeadLetterExchange() {
        return new DirectExchange(EventNames.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue deliveryEventsQueue() {
        return QueueBuilder.durable(EventNames.DELIVERY_QUEUE)
                .deadLetterExchange(EventNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventNames.DELIVERY_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue paymentEventsQueue() {
        return QueueBuilder.durable(EventNames.PAYMENT_QUEUE)
                .deadLetterExchange(EventNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventNames.PAYMENT_DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    Queue deliveryDeadLetterQueue() {
        return QueueBuilder.durable(EventNames.DELIVERY_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(EventNames.PAYMENT_DEAD_LETTER_QUEUE).build();
    }

    /** Courier assignment and delivery progress (contracts/events/delivery-operations). */
    @Bean
    Binding deliveryStatusBinding(Queue deliveryEventsQueue, TopicExchange deliveryOperationsExchange) {
        return BindingBuilder.bind(deliveryEventsQueue).to(deliveryOperationsExchange).with("delivery.*");
    }

    /** Warehouse arrival and transfers: parcel.received-at-warehouse, parcel.transferred. */
    @Bean
    Binding warehouseParcelBinding(Queue deliveryEventsQueue, TopicExchange deliveryOperationsExchange) {
        return BindingBuilder.bind(deliveryEventsQueue).to(deliveryOperationsExchange).with("parcel.*");
    }

    /** payment.succeeded / failed / refunded (contracts/events/payment-settlement-service). */
    @Bean
    Binding paymentEventsBinding(Queue paymentEventsQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentEventsQueue).to(paymentExchange).with("payment.*");
    }

    @Bean
    Binding deliveryDeadLetterBinding(Queue deliveryDeadLetterQueue, DirectExchange parcelPricingDeadLetterExchange) {
        return BindingBuilder.bind(deliveryDeadLetterQueue).to(parcelPricingDeadLetterExchange)
                .with(EventNames.DELIVERY_DEAD_LETTER_QUEUE);
    }

    @Bean
    Binding paymentDeadLetterBinding(Queue paymentDeadLetterQueue, DirectExchange parcelPricingDeadLetterExchange) {
        return BindingBuilder.bind(paymentDeadLetterQueue).to(parcelPricingDeadLetterExchange)
                .with(EventNames.PAYMENT_DEAD_LETTER_QUEUE);
    }
}
