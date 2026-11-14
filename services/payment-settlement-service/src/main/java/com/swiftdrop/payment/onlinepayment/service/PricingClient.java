package com.swiftdrop.payment.onlinepayment.service;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Context;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PricingClient {
    private final Environment env;
    private final JsonMapper json;
    private final ResourceLoader resources;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public PricingClient(Environment env, JsonMapper json, ResourceLoader resources) {
        this.env=env; this.json=json; this.resources=resources;
        if(env.getProperty("payment.pricing.stub-enabled",Boolean.class,false)
            && (!env.acceptsProfiles(Profiles.of("local")) || env.acceptsProfiles(Profiles.of("prod","production"))))
            throw new IllegalStateException("Pricing stub is permitted only in the local profile, never production");
    }
    public Context lookup(String bookingId, Actor actor) {
        Context c;
        try {
            if(env.getProperty("payment.pricing.stub-enabled",Boolean.class,false)) {
                try(var in=resources.getResource(env.getRequiredProperty("payment.pricing.stub-resource")).getInputStream()) {
                    c=json.readValue(in,Context.class);
                }
                if(!bookingId.equals(c.bookingId())) throw new PaymentProblem(404,"NOT_FOUND","Booking was not found.");
            } else {
                String url=env.getRequiredProperty("payment.pricing.base-url")+"/api/v1/bookings/"+
                    URLEncoder.encode(bookingId,StandardCharsets.UTF_8).replace("+","%20")+"/payment-context";
                var r=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Authorization","Bearer "+actor.token()).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(Set.of(401,403,404,409).contains(r.statusCode()))
                    throw new PaymentProblem(r.statusCode(), r.statusCode()==404?"NOT_FOUND":r.statusCode()==403?"FORBIDDEN":r.statusCode()==401?"UNAUTHENTICATED":"CONFLICT",
                        "Booking cannot be used for this payment.");
                if(r.statusCode()!=200) throw PaymentProblem.unavailable();
                c=json.readValue(r.body(),Context.class);
            }
        } catch(PaymentProblem e) { throw e; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw PaymentProblem.unavailable(); }
        catch(Exception e) { throw PaymentProblem.unavailable(); }
        validate(c,bookingId);
        actor.requireOwner(c.customerId(),c.merchantId());
        if("DIRECT_WEBSITE".equals(c.channel()) && !actor.admin()) throw PaymentProblem.forbidden();
        if(!c.payable()) throw PaymentProblem.conflict("Booking is not payable.");
        return c;
    }
    static void validate(Context c, String bookingId) {
        if(c==null || !bookingId.equals(c.bookingId()) || c.pricingSnapshotId()==null
            || c.pricingSnapshotId().isBlank() || c.pricingSnapshotId().length()>64 || c.amount()==null
            || c.amount().signum()<=0 || c.amount().scale()>2 || c.amount().precision()-c.amount().scale()>10
            || !"LKR".equals(c.currency()) || !Set.of("DIRECT_WEBSITE","MERCHANT_API").contains(Objects.toString(c.channel(),""))
            || ("MERCHANT_API".equals(c.channel()) && c.merchantId()==null)
            || ("DIRECT_WEBSITE".equals(c.channel()) && (c.customerId()==null || c.merchantId()!=null)) || c.payer()==null)
            throw PaymentProblem.unavailable();
        var p=c.payer();
        for(String s:Arrays.asList(p.firstName(),p.lastName(),p.email(),p.phone(),p.address(),p.city(),p.country()))
            if(s==null || s.isBlank() || s.length()>500) throw PaymentProblem.unavailable();
        if(!p.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || !p.phone().matches("\\+?[0-9 ()-]{7,25}"))
            throw PaymentProblem.unavailable();
    }
}
