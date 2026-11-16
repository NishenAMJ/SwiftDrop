package com.swiftdrop.communication.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String IDENTITY_EXCHANGE = "swiftdrop.identity";
    public static final String PARCEL_EXCHANGE = "swiftdrop.parcel";
    public static final String DELIVERY_EXCHANGE = "swiftdrop.delivery";

    public static final String IDENTITY_QUEUE = "notification-webhook-service.identity-events";
    public static final String PARCEL_QUEUE = "notification-webhook-service.parcel-notifications";
    public static final String DEAD_LETTER_QUEUE = "notification-webhook-service.dead-letter";

    public static final String ROUTING_KEY_PARCEL_STATUS_CHANGED = "parcel.status-changed";

    @Bean
    public TopicExchange identityExchange() {
        return new TopicExchange(IDENTITY_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange parcelExchange() {
        return new TopicExchange(PARCEL_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(DELIVERY_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Queue identityEventsQueue() {
        return QueueBuilder.durable(IDENTITY_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Queue parcelNotificationsQueue() {
        return QueueBuilder.durable(PARCEL_QUEUE)
                .deadLetterExchange("")
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding userEventsBinding(Queue identityEventsQueue, TopicExchange identityExchange) {
        return BindingBuilder.bind(identityEventsQueue).to(identityExchange).with("user.*");
    }

    @Bean
    public Binding merchantEventsBinding(Queue identityEventsQueue, TopicExchange identityExchange) {
        return BindingBuilder.bind(identityEventsQueue).to(identityExchange).with("merchant.*");
    }

    @Bean
    public Binding parcelStatusFromParcelExchangeBinding(Queue parcelNotificationsQueue, TopicExchange parcelExchange) {
        return BindingBuilder.bind(parcelNotificationsQueue).to(parcelExchange).with(ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    @Bean
    public Binding parcelStatusFromDeliveryExchangeBinding(Queue parcelNotificationsQueue, TopicExchange deliveryExchange) {
        return BindingBuilder.bind(parcelNotificationsQueue).to(deliveryExchange).with(ROUTING_KEY_PARCEL_STATUS_CHANGED);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);

        MethodInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxRetries(3)
                .backOffOptions(1000, 1.0, 1000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();

        factory.setAdviceChain(retryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
