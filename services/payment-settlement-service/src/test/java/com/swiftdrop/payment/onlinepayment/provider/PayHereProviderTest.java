package com.swiftdrop.payment.onlinepayment.provider;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Payer;
import com.swiftdrop.payment.onlinepayment.entity.Payment;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class PayHereProviderTest {
    private final String order="f2249d4c-a134-4bc0-b9ed-ffea97ad8791";
    private final MockEnvironment env=new MockEnvironment().withProperty("payment.payhere.merchant-id","123")
        .withProperty("payment.payhere.merchant-secret","test-secret").withProperty("payment.payhere.base-url","https://sandbox.payhere.lk")
        .withProperty("payment.payhere.return-url","https://example.com/return").withProperty("payment.payhere.cancel-url","https://example.com/cancel")
        .withProperty("payment.payhere.notify-url","https://example.com/notify");
    private final PayHereProvider provider=new PayHereProvider(env,JsonMapper.builder().build());
    private Map<String,String> callback() {
        Map<String,String> f=new HashMap<>(Map.of("merchant_id","123","order_id",order,"payhere_amount","1000.00",
            "payhere_currency","LKR","status_code","2","payment_id","123456"));
        f.put("md5sig",PayHereProvider.digest("MD5","123"+order+"1000.00LKR2"+PayHereProvider.digest("MD5","test-secret").toUpperCase(Locale.ROOT)).toUpperCase(Locale.ROOT));
        return f;
    }
    @Test void validCallbackAndStableFingerprint() {
        var first=provider.verify(callback()); var second=provider.verify(callback());
        assertEquals(new BigDecimal("1000.00"),first.amount()); assertEquals(first.fingerprint(),second.fingerprint());
    }
    @Test void alteredAmountIsRejected() {
        var f=callback(); f.put("payhere_amount","1.00"); assertThrows(PaymentProblem.class,()->provider.verify(f));
    }
    @Test void wrongMerchantAndMissingSignatureAreRejected() {
        var f=callback(); f.put("merchant_id","456"); assertThrows(PaymentProblem.class,()->provider.verify(f));
        var missing=callback(); missing.remove("md5sig"); assertThrows(PaymentProblem.class,()->provider.verify(missing));
    }
    @Test void checkoutContainsHashButNoSecret() {
        Payment p=new Payment(); p.setId(UUID.fromString(order)); p.setAmount(new BigDecimal("1000")); p.setCurrency("LKR"); p.setBookingId("SWD-1");
        var c=provider.checkout(p,new Payer("Test","Payer","test@example.com","0770000000","Test address","Colombo","Sri Lanka"));
        assertEquals("POST",c.method()); assertEquals("1000.00",c.fields().get("amount"));
        assertEquals(32,c.fields().get("hash").length()); assertFalse(c.fields().containsValue("test-secret"));
    }
    @Test void successRequiresReference() {
        var f=callback(); f.remove("payment_id"); assertThrows(PaymentProblem.class,()->provider.verify(f));
    }

    @Test void partialRefundUsesPayHereDocumentedRequestAndBearerToken() throws Exception {
        var refundBody=new AtomicReference<String>();
        var authorization=new AtomicReference<String>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/merchant/v1/oauth/token",exchange->{
            exchange.getRequestBody().readAllBytes();
            byte[] response="{\"access_token\":\"test-access-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            try(var body=exchange.getResponseBody()){ body.write(response); }
        });
        server.createContext("/merchant/v1/payment/refund",exchange->{
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            refundBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] response="{\"status\":1,\"data\":560034010257}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);
            try(var body=exchange.getResponseBody()){ body.write(response); }
        });
        server.start();
        try {
            var testEnv=new MockEnvironment()
                .withProperty("payment.payhere.base-url","http://127.0.0.1:"+server.getAddress().getPort())
                .withProperty("payment.payhere.app-id","test-app")
                .withProperty("payment.payhere.app-secret","test-secret");
            var testProvider=new PayHereProvider(testEnv,JsonMapper.builder().build());
            var result=testProvider.refund("320027150501",new BigDecimal("100.50"),"Partial refund test");

            assertEquals("SUCCESS",result.outcome());
            assertEquals("560034010257",result.reference());
            assertEquals("Bearer test-access-token",authorization.get());
            var sent=JsonMapper.builder().build().readTree(refundBody.get());
            assertEquals("320027150501",sent.path("payment_id").asText());
            assertEquals("100.50",sent.path("amount").asText());
            assertEquals("Partial refund test",sent.path("description").asText());
            assertFalse(sent.has("authorization_token"));
        } finally {
            server.stop(0);
        }
    }
}
