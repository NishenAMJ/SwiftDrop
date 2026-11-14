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
}
