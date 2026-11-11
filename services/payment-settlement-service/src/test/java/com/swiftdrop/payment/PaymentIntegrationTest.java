package com.swiftdrop.payment;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import com.swiftdrop.payment.onlinepayment.provider.PaymentProvider;
import com.swiftdrop.payment.onlinepayment.service.*;
import com.swiftdrop.payment.onlinepayment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against a disposable PostgreSQL database, never the development database. */
@SpringBootTest(properties={"payment.workers.enabled=false","spring.config.import=","spring.flyway.create-schemas=true",
    "spring.rabbitmq.password=payment-test-only","spring.rabbitmq.username=paymenttest",
    "spring.rabbitmq.host=localhost","spring.rabbitmq.port=56785","payment.payhere.merchant-id=test","debug=false"})
@EnabledIfEnvironmentVariable(named="PAYMENT_TEST_DB_URL",matches=".+")
class PaymentIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->System.getenv("PAYMENT_TEST_DB_URL"));
        p.add("spring.datasource.username",()->"postgres"); p.add("spring.datasource.password",()->"payment-test-only");
    }
    @Autowired PaymentService service;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @Autowired org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Autowired org.springframework.amqp.core.AmqpAdmin rabbitAdmin;
    @MockitoBean PricingClient pricing;
    @MockitoBean PaymentProvider provider;
    final UUID customerId=UUID.fromString("f2249d4c-a134-4bc0-b9ed-ffea97ad8791");
    final UUID merchantId=UUID.fromString("846c790e-497a-4340-8039-79e25c95f40e");
    final Actor customer=new Actor(customerId,null,Set.of("CUSTOMER"),"test");
    final Actor admin=new Actor(UUID.fromString("a2249d4c-a134-4bc0-b9ed-ffea97ad8791"),null,Set.of("DISPATCHER"),"test");
    @BeforeEach void setup() {
        db.execute("TRUNCATE payment_settlement.payment_callbacks,payment_settlement.payment_transactions,payment_settlement.payment_outbox,payment_settlement.payment_booking_claims,payment_settlement.payments CASCADE");
        when(pricing.lookup(anyString(),any())).thenAnswer(a->new Context(a.getArgument(0),"MERCHANT_API",customerId,merchantId,true,
            UUID.randomUUID(),new BigDecimal("1000.00"),"LKR",new Payer("Test","Payer","test@example.com","0770000000","Address","Colombo","Sri Lanka")));
        when(provider.checkout(any(),any())).thenReturn(new Checkout("PAYHERE","https://sandbox.payhere.lk/pay/checkout","POST",Map.of("hash","test")));
    }
    UUID create(String booking,String key) { return UUID.fromString(service.create(new Create(booking),key,customer).get("id").toString()); }
    UUID paid() {
        UUID id=create("BOOK-1","payment-key-000001");
        when(provider.verify(anyMap())).thenReturn(new PaymentProvider.VerifiedCallback(id,"123456",new BigDecimal("1000"),"LKR",2,"a".repeat(64)));
        service.callback(Map.of());
        when(provider.retrieve(id)).thenReturn(List.of(new PaymentProvider.RemotePayment("123456",id.toString(),new BigDecimal("1000"),"LKR","RECEIVED")));
        return id;
    }
    @Test void migrationAndIdempotentCreate() {
        UUID id=create("BOOK-1","payment-key-000001"); assertEquals(id,create("BOOK-1","payment-key-000001"));
        assertThrows(PaymentProblem.class,()->create("BOOK-2","payment-key-000001"));
        assertThrows(PaymentProblem.class,()->create("BOOK-1","payment-key-000002"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_settlement.payments",Integer.class));
    }
    @Test void duplicateCallbackPublishesOneEventAndOwnershipIsEnforced() {
        UUID id=paid(); service.callback(Map.of());
        assertEquals(PaymentStatus.SUCCESS,service.get(id,customer).status());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_settlement.payment_outbox",Integer.class));
        Actor stranger=new Actor(UUID.randomUUID(),null,Set.of("CUSTOMER"),"test");
        assertThrows(PaymentProblem.class,()->service.get(id,stranger));
        assertEquals(0,service.history(stranger,0,20,"createdAt,desc").totalElements());
    }
    @Test void partialThenRemainingRefundAndReplay() {
        UUID id=paid(); when(provider.refund(anyString(),any(),anyString())).thenReturn(new PaymentProvider.RefundResult("SUCCESS","987654"));
        Refund first=service.refund(id,new RefundRequest(new BigDecimal("250.00"),"Adjustment"),"refund-key-000001",admin);
        assertEquals("SUCCESS",first.status()); assertEquals(PaymentStatus.PARTIALLY_REFUNDED,service.get(id,customer).status());
        assertEquals(first.id(),service.refund(id,new RefundRequest(new BigDecimal("250.00"),"Adjustment"),"refund-key-000001",admin).id());
        service.refund(id,new RefundRequest(new BigDecimal("750.00"),"Remaining"),"refund-key-000002",admin);
        assertEquals(PaymentStatus.REFUNDED,service.get(id,customer).status());
        verify(provider,times(2)).refund(anyString(),any(),anyString());
    }
    @Test void uncertainRefundIsNeverBlindlyRetried() {
        UUID id=paid(); when(provider.refund(anyString(),any(),anyString())).thenReturn(new PaymentProvider.RefundResult("UNKNOWN",null));
        Refund r=service.refund(id,new RefundRequest(new BigDecimal("250"),"Adjustment"),"refund-key-000001",admin);
        assertEquals("PENDING",r.status());
        service.refund(id,new RefundRequest(new BigDecimal("250"),"Adjustment"),"refund-key-000001",admin);
        service.submitRefund(r.id());
        assertThrows(PaymentProblem.class,()->service.refund(id,new RefundRequest(new BigDecimal("250"),"Another"),"refund-key-000002",admin));
        verify(provider,times(1)).refund(anyString(),any(),anyString());
    }
    @Test void failedRefundReleasesReservation() {
        UUID id=paid(); when(provider.refund(anyString(),any(),anyString())).thenReturn(new PaymentProvider.RefundResult("FAILED",null));
        assertEquals("FAILED",service.refund(id,new RefundRequest(new BigDecimal("250"),"Adjustment"),"refund-key-000001",admin).status());
        assertEquals(0,db.queryForObject("SELECT reserved_amount FROM payment_settlement.payments WHERE id=?",BigDecimal.class,id).signum());
        assertEquals(PaymentStatus.SUCCESS,service.get(id,customer).status());
    }
    @Test void concurrentCreatesUseOneRecord() throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=executor.invokeAll(List.of(()->create("BOOK-1","payment-key-000001"),()->create("BOOK-1","payment-key-000001")));
            assertEquals(futures.get(0).get(),futures.get(1).get());
        }
    }
    @Test void refundCannotExceedPaidAmountOrBeCreatedByCustomer() {
        UUID id=paid();
        assertThrows(PaymentProblem.class,()->service.refund(id,new RefundRequest(new BigDecimal("1000.01"),"Too much"),"refund-key-000001",admin));
        assertThrows(PaymentProblem.class,()->service.refund(id,new RefundRequest(new BigDecimal("10"),"Forbidden"),"refund-key-000001",customer));
    }
    @Test void lateVerifiedRetrievalRecoversMissedCallback() {
        UUID id=create("BOOK-1","payment-key-000001");
        when(provider.retrieve(id)).thenReturn(List.of(new PaymentProvider.RemotePayment("123456",id.toString(),new BigDecimal("1000"),"LKR","RECEIVED")));
        service.reconcile(id); service.reconcile(id);
        assertEquals(PaymentStatus.SUCCESS,service.get(id,customer).status());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_settlement.payment_outbox",Integer.class));
    }
    @Test @EnabledIfEnvironmentVariable(named="PAYMENT_TEST_RABBIT",matches="true")
    void eventSurvivesPublishFailureThenReachesDurableQueue() {
        paid();
        var failing=mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        doThrow(new IllegalStateException("Broker unavailable")).when(failing).send(anyString(),anyString(),any(org.springframework.amqp.core.Message.class),any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
        new com.swiftdrop.payment.events.OutboxPublisher(db,tx,failing).publish();
        assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_settlement.payment_outbox WHERE published_at IS NULL AND attempts=1",Integer.class));
        ((org.springframework.amqp.rabbit.core.RabbitAdmin)rabbitAdmin).initialize();
        rabbitAdmin.purgeQueue("payment-settlement-service.audit");
        db.update("UPDATE payment_settlement.payment_outbox SET next_attempt_at=timezone('UTC',now())");
        new com.swiftdrop.payment.events.OutboxPublisher(db,tx,rabbit).publish();
        assertEquals(1,db.queryForObject("SELECT count(*) FROM payment_settlement.payment_outbox WHERE published_at IS NOT NULL",Integer.class));
        var message=rabbit.receive("payment-settlement-service.audit",5000);
        assertNotNull(message); assertEquals("application/json",message.getMessageProperties().getContentType());
        assertNotNull(message.getMessageProperties().getMessageId());
    }
}
