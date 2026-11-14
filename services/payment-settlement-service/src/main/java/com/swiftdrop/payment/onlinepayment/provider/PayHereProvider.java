package com.swiftdrop.payment.onlinepayment.provider;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import com.swiftdrop.payment.onlinepayment.entity.Payment;
import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import java.math.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PayHereProvider implements PaymentProvider {
    private static final Logger log=LoggerFactory.getLogger(PayHereProvider.class);
    private final Environment env;
    private final JsonMapper json;
    private final HttpClient http;
    public PayHereProvider(Environment env, JsonMapper json) {
        this.env=env; this.json=json;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    private String setting(String key) {
        String value=env.getProperty("payment.payhere."+key, "");
        if(value.isBlank()) throw new PaymentProblem(503,"PROVIDER_NOT_CONFIGURED","Payment provider is not configured.");
        return value;
    }
    private String base() { return setting("base-url"); }
    public static String digest(String algorithm, String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hash(String merchant, String order, String amount, String currency, String secret) {
        return digest("MD5", merchant+order+amount+currency+digest("MD5",secret).toUpperCase(Locale.ROOT)).toUpperCase(Locale.ROOT);
    }
    @Override public Checkout checkout(Payment p, Payer payer) {
        String amount=p.getAmount().setScale(2,RoundingMode.UNNECESSARY).toPlainString();
        Map<String,String> f=new LinkedHashMap<>();
        f.put("merchant_id",setting("merchant-id")); f.put("order_id",p.getId().toString());
        f.put("amount",amount); f.put("currency",p.getCurrency()); f.put("items","SwiftDrop delivery "+p.getBookingId());
        f.put("return_url",setting("return-url")); f.put("cancel_url",setting("cancel-url")); f.put("notify_url",setting("notify-url"));
        f.put("hash",hash(f.get("merchant_id"),f.get("order_id"),amount,p.getCurrency(),setting("merchant-secret")));
        f.put("first_name",payer.firstName()); f.put("last_name",payer.lastName()); f.put("email",payer.email());
        f.put("phone",payer.phone()); f.put("address",payer.address()); f.put("city",payer.city()); f.put("country",payer.country());
        return new Checkout("PAYHERE",base()+"/pay/checkout","POST",f);
    }
    @Override public VerifiedCallback verify(Map<String,String> f) {
        try {
            for(String key:List.of("merchant_id","order_id","payhere_amount","payhere_currency","status_code","md5sig"))
                if(f.get(key)==null || f.get(key).isBlank()) throw new IllegalArgumentException();
            String expected=digest("MD5", f.get("merchant_id")+f.get("order_id")+f.get("payhere_amount")+
                f.get("payhere_currency")+f.get("status_code")+digest("MD5",setting("merchant-secret")).toUpperCase(Locale.ROOT)).toUpperCase(Locale.ROOT);
            if(!setting("merchant-id").equals(f.get("merchant_id")) || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), f.get("md5sig").toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException();
            int status=Integer.parseInt(f.get("status_code"));
            if(!Set.of(2,0,-1,-2,-3).contains(status)) throw new IllegalArgumentException();
            String ref=f.get("payment_id");
            if((status==2 || status==-3) && (ref==null || !ref.matches("[0-9]{1,30}"))) throw new IllegalArgumentException();
            BigDecimal amount=new BigDecimal(f.get("payhere_amount")).setScale(2,RoundingMode.UNNECESSARY);
            if(amount.signum()<=0) throw new IllegalArgumentException();
            // payment_id is not covered by PayHere's checksum: retrieve before trusting it for a refund.
            return new VerifiedCallback(UUID.fromString(f.get("order_id")),ref,amount,f.get("payhere_currency"),status,
                digest("SHA-256",expected+"|"+Objects.toString(ref,"")));
        } catch(IllegalArgumentException | ArithmeticException e) {
            throw new PaymentProblem(400,"INVALID_CALLBACK","The provider notification is invalid.");
        }
    }
    private JsonNode request(HttpRequest request) {
        try {
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300) {
                log.warn("PayHere request failed endpoint={} status={}",request.uri().getPath(),response.statusCode());
                throw PaymentProblem.unavailable();
            }
            return json.readTree(response.body());
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); log.warn("PayHere request interrupted endpoint={}",request.uri().getPath()); throw PaymentProblem.unavailable(); }
        catch(java.io.IOException | tools.jackson.core.JacksonException e) { log.warn("PayHere request failed endpoint={} error={}",request.uri().getPath(),e.getClass().getSimpleName()); throw PaymentProblem.unavailable(); }
    }
    private String token() {
        String credentials=Base64.getEncoder().encodeToString((setting("app-id")+":"+setting("app-secret")).getBytes(StandardCharsets.UTF_8));
        JsonNode response=request(HttpRequest.newBuilder(URI.create(base()+"/merchant/v1/oauth/token"))
            .timeout(Duration.ofSeconds(5)).header("Authorization","Basic "+credentials)
            .header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build());
        String token=response.path("access_token").asText("");
        if(token.isBlank()) throw PaymentProblem.unavailable();
        return token;
    }
    @Override public RefundResult refund(String reference, BigDecimal amount, String reason) {
        // No automatic HTTP retry: a lost response may already have moved money.
        try {
            JsonNode response=request(HttpRequest.newBuilder(URI.create(base()+"/merchant/v1/payment/refund"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("payment_id",reference,
                    "amount",amount.setScale(2).toPlainString(),"description",reason)))).build());
            int status=response.path("status").asInt(Integer.MIN_VALUE);
            String refundReference=response.path("data").asText("");
            log.info("PayHere refund response status={} hasReference={}",status,refundReference.matches("[0-9]{1,30}"));
            if(status==1 && refundReference.matches("[0-9]{1,30}")) return new RefundResult("SUCCESS",refundReference);
            if(status==0 || status==-1) return new RefundResult("FAILED",null);
            return new RefundResult("UNKNOWN",null);
        } catch(RuntimeException e) {
            log.warn("PayHere refund request failed paymentReference={} error={}",reference,e.getClass().getSimpleName());
            return new RefundResult("UNKNOWN",null);
        }
    }
    @Override public List<RemotePayment> retrieve(UUID orderId) {
        JsonNode root=request(HttpRequest.newBuilder(URI.create(base()+"/merchant/v1/payment/search?order_id="+orderId))
            .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token()).GET().build());
        int responseStatus=root.path("status").asInt(Integer.MIN_VALUE);
        if(responseStatus==-1) {
            log.info("PayHere retrieval completed orderId={} responseStatus={} matches=0",orderId,responseStatus);
            return List.of();
        }
        if(!root.path("data").isArray()) throw PaymentProblem.unavailable();
        List<RemotePayment> result=new ArrayList<>();
        for(JsonNode n:root.path("data")) {
            try { result.add(new RemotePayment(n.path("payment_id").asText(), n.path("order_id").asText(),
                new BigDecimal(n.path("amount").asText()),n.path("currency").asText(),n.path("status").asText())); }
            catch(RuntimeException e) { throw PaymentProblem.unavailable(); }
        }
        log.info("PayHere retrieval completed orderId={} responseStatus={} matches={} paymentStatus={}",
            orderId,responseStatus,result.size(),result.size()==1?result.getFirst().status():"MULTIPLE_OR_NONE");
        return result;
    }
}
