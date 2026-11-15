package com.swiftdrop.tracking.config;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String PARCEL_EXCHANGE = "swiftdrop.parcel-pricing";
    public static final String DELIVERY_EXCHANGE = "swiftdrop.delivery-operations";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.tracking-reporting.dlx";

    public static final String PARCEL_QUEUE = "tracking-reporting-service.parcel-pricing-events";
    public static final String DELIVERY_QUEUE = "tracking-reporting-service.delivery-events";
    public static final String PARCEL_DEAD_LETTER_QUEUE = PARCEL_QUEUE + ".dead-letter";
    public static final String DELIVERY_DEAD_LETTER_QUEUE = DELIVERY_QUEUE + ".dead-letter";

    private static final String PARCEL_DEAD_LETTER_KEY = "dead-letter.parcel-pricing";
    private static final String DELIVERY_DEAD_LETTER_KEY = "dead-letter.delivery-operations";

    @Bean
    TopicExchange parcelPricingExchange() {
        return new TopicExchange(PARCEL_EXCHANGE, true, false);
    }

    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange(DELIVERY_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange trackingDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue parcelPricingQueue() {
        return durableQueue(PARCEL_QUEUE, PARCEL_DEAD_LETTER_KEY);
    }

    @Bean
    Queue deliveryQueue() {
        return durableQueue(DELIVERY_QUEUE, DELIVERY_DEAD_LETTER_KEY);
    }

    @Bean
    Queue parcelDeadLetterQueue() {
        return QueueBuilder.durable(PARCEL_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Queue deliveryDeadLetterQueue() {
        return QueueBuilder.durable(DELIVERY_DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding parcelEventsBinding(Queue parcelPricingQueue, TopicExchange parcelPricingExchange) {
        return BindingBuilder.bind(parcelPricingQueue).to(parcelPricingExchange).with("parcel.*");
    }

    @Bean
    Binding deliveryParcelEventsBinding(Queue deliveryQueue, TopicExchange deliveryOperationsExchange) {
        return BindingBuilder.bind(deliveryQueue).to(deliveryOperationsExchange).with("parcel.*");
    }

    @Bean
    Binding deliveryStatusEventsBinding(Queue deliveryQueue, TopicExchange deliveryOperationsExchange) {
        return BindingBuilder.bind(deliveryQueue).to(deliveryOperationsExchange).with("delivery.*");
    }

    @Bean
    Binding parcelDeadLetterBinding(Queue parcelDeadLetterQueue, DirectExchange trackingDeadLetterExchange) {
        return BindingBuilder.bind(parcelDeadLetterQueue)
                .to(trackingDeadLetterExchange)
                .with(PARCEL_DEAD_LETTER_KEY);
    }

    @Bean
    Binding deliveryDeadLetterBinding(Queue deliveryDeadLetterQueue, DirectExchange trackingDeadLetterExchange) {
        return BindingBuilder.bind(deliveryDeadLetterQueue)
                .to(trackingDeadLetterExchange)
                .with(DELIVERY_DEAD_LETTER_KEY);
    }

    private Queue durableQueue(String name, String deadLetterRoutingKey) {
        return QueueBuilder.durable(name)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", deadLetterRoutingKey))
                .build();
    }
}
