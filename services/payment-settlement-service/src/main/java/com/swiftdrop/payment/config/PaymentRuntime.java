package com.swiftdrop.payment.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@EnableScheduling
public class PaymentRuntime {
    @Bean TransactionTemplate paymentTransactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    @Bean TopicExchange paymentExchange() { return new TopicExchange("swiftdrop.payment",true,false); }
    // Durable holding queue prevents publication loss before consumer teams bind their queues.
    @Bean Queue paymentAuditQueue() { return QueueBuilder.durable("payment-settlement-service.audit").build(); }
    @Bean Binding paymentAuditBinding(TopicExchange paymentExchange,Queue paymentAuditQueue) {
        return BindingBuilder.bind(paymentAuditQueue).to(paymentExchange).with("payment.*");
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange("swiftdrop.delivery-operations", true, false);
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange("swiftdrop.payment.dlx", true, false);
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    Queue codCollectionQueue() {
        return QueueBuilder.durable("payment-settlement-service.cod")
            .deadLetterExchange("swiftdrop.payment.dlx")
            .deadLetterRoutingKey("cod.failed")
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    Binding codCollectionBinding(TopicExchange deliveryOperationsExchange, Queue codCollectionQueue) {
        return BindingBuilder.bind(codCollectionQueue).to(deliveryOperationsExchange)
            .with("delivery.cod-collected");
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    Queue codDeadLetterQueue() {
        return QueueBuilder.durable("payment-settlement-service.cod.dlq").build();
    }

    @Bean
    @ConditionalOnProperty(name = "payment.cod.consumer.enabled", havingValue = "true")
    Binding codDeadLetterBinding(DirectExchange paymentDeadLetterExchange, Queue codDeadLetterQueue) {
        return BindingBuilder.bind(codDeadLetterQueue).to(paymentDeadLetterExchange).with("cod.failed");
    }
}
