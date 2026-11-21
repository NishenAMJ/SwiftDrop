package com.swiftdrop.payment.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.amqp.core.*;

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

    // The COD exchange, queue and bindings are always declared, even while the consumer is switched off.
    // A topic exchange drops a message that no queue is bound to, so declaring only when consuming would
    // lose every COD collection published before the consumer is enabled. The listener stays conditional.
    @Bean
    TopicExchange deliveryOperationsExchange() {
        return new TopicExchange("swiftdrop.delivery-operations", true, false);
    }

    @Bean
    DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange("swiftdrop.payment.dlx", true, false);
    }

    @Bean
    Queue codCollectionQueue() {
        return QueueBuilder.durable("payment-settlement-service.cod")
            .deadLetterExchange("swiftdrop.payment.dlx")
            .deadLetterRoutingKey("cod.failed")
            .build();
    }

    @Bean
    Binding codCollectionBinding(TopicExchange deliveryOperationsExchange, Queue codCollectionQueue) {
        return BindingBuilder.bind(codCollectionQueue).to(deliveryOperationsExchange)
            .with("delivery.cod-collected");
    }

    @Bean
    Queue codDeadLetterQueue() {
        return QueueBuilder.durable("payment-settlement-service.cod.dlq").build();
    }

    @Bean
    Binding codDeadLetterBinding(DirectExchange paymentDeadLetterExchange, Queue codDeadLetterQueue) {
        return BindingBuilder.bind(codDeadLetterQueue).to(paymentDeadLetterExchange).with("cod.failed");
    }
}
