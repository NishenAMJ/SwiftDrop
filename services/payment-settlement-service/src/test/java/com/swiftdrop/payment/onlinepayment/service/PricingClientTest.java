package com.swiftdrop.payment.onlinepayment.service;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class PricingClientTest {
    @Test void actorRolesDoNotReplaceOwnership() {
        UUID user=UUID.randomUUID(),merchant=UUID.randomUUID();
        Actor a=new Actor(user,merchant,Set.of("CUSTOMER","MERCHANT"),"token");
        assertTrue(a.owns(user,null)); assertTrue(a.owns(null,merchant)); assertFalse(a.owns(UUID.randomUUID(),UUID.randomUUID()));
        assertThrows(PaymentProblem.class,a::requireAdmin);
    }
    @Test void stubCannotStartOutsideLocalProfile() {
        var env=new MockEnvironment().withProperty("payment.pricing.stub-enabled","true");
        assertThrows(IllegalStateException.class,()->new PricingClient(env,JsonMapper.builder().build(),new DefaultResourceLoader()));
        env.setActiveProfiles("local","production");
        assertThrows(IllegalStateException.class,()->new PricingClient(env,JsonMapper.builder().build(),new DefaultResourceLoader()));
    }
    @Test void invalidPriceAndMissingOwnershipAreRejected() {
        var payer=new Payer("Test","Payer","test@example.com","0770000000","Address","Colombo","Sri Lanka");
        var c=new Context("B","DIRECT_WEBSITE",UUID.randomUUID(),null,true,UUID.randomUUID(),new BigDecimal("100.00"),"LKR",payer);
        assertDoesNotThrow(()->PricingClient.validate(c,"B"));
        assertThrows(PaymentProblem.class,()->PricingClient.validate(c,"OTHER"));
        assertThrows(PaymentProblem.class,()->PricingClient.validate(new Context("B","MERCHANT_API",null,null,true,UUID.randomUUID(),new BigDecimal("100"),"LKR",payer),"B"));
    }
    @Test void idempotencyKeysAreBounded() {
        assertDoesNotThrow(()->PaymentService.key("unique-key-123456"));
        assertThrows(PaymentProblem.class,()->PaymentService.key("short"));
        assertThrows(PaymentProblem.class,()->PaymentService.key("x".repeat(129)));
    }
}
