package com.swiftdrop.parcel_pricing_service.client;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.parcel_pricing_service.common.ApiException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class BookingPaymentVerifierTest {
    @Test void retrievesAuthoritativePaymentAndForwardsCustomerAuthorization() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        UUID paymentId=UUID.randomUUID();
        server.createContext("/api/v1/payments/"+paymentId,exchange->{
            assertEquals("Bearer customer-token",exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body=("{\"id\":\""+paymentId+"\",\"bookingId\":\"booking-test\",\"amount\":500.00,\"currency\":\"LKR\",\"status\":\"SUCCESS\",\"providerReference\":\"123456\",\"extraField\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client=new BookingPaymentVerifier("http://127.0.0.1:"+server.getAddress().getPort(),JsonMapper.builder().build());
            var jwt=Jwt.withTokenValue("customer-token").header("alg","RS256").subject(UUID.randomUUID().toString()).build();
            var payment=client.verify(paymentId,jwt);
            assertEquals("SUCCESS",payment.status()); assertEquals("booking-test",payment.bookingId());
            assertEquals("123456",payment.providerReference());
        } finally { server.stop(0); }
    }
    @Test void unauthenticatedVerificationIsRejected() {
        var client=new BookingPaymentVerifier("http://127.0.0.1:1",JsonMapper.builder().build());
        assertThrows(ApiException.class,()->client.verify(UUID.randomUUID(),null));
    }
}
