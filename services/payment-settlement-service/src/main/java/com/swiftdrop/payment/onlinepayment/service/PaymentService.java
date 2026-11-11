package com.swiftdrop.payment.onlinepayment.service;

import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.*;
import com.swiftdrop.payment.onlinepayment.entity.Payment;
import com.swiftdrop.payment.onlinepayment.enums.PaymentStatus;
import com.swiftdrop.payment.onlinepayment.provider.PaymentProvider;
import com.swiftdrop.payment.onlinepayment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final PricingClient pricing;
    private final PaymentProvider provider;
    private final JsonMapper json;
    public PaymentService(PaymentRepository payments, JdbcTemplate db, TransactionTemplate tx,
                          PricingClient pricing, PaymentProvider provider, JsonMapper json) {
        this.payments=payments; this.db=db; this.tx=tx; this.pricing=pricing; this.provider=provider; this.json=json;
    }
    static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    public static void key(String key) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{16,128}"))
            throw new PaymentProblem(400,"VALIDATION_FAILED","Idempotency-Key must contain 16 to 128 letters, digits, dots, underscores, colons or hyphens.");
    }
    private Payment required(UUID id, boolean lock) {
        return (lock?payments.lock(id):payments.findById(id)).orElseThrow(()->new PaymentProblem(404,"NOT_FOUND","Payment was not found."));
    }
    public View get(UUID id, Actor actor) {
        Payment p=required(id,false); actor.requireOwner(p.getCustomerId(),p.getMerchantId()); return view(p);
    }
    public PaymentDtos.Page<View> history(Actor actor, int page, int size, String sort) {
        if(page<0 || size<1 || size>100 || !Set.of("createdAt,desc","createdAt,asc").contains(sort))
            throw new PaymentProblem(400,"VALIDATION_FAILED","Invalid pagination or sort. Use createdAt,asc or createdAt,desc.");
        var result=payments.findAll((root,q,cb)-> {
            if(actor.admin()) return cb.conjunction();
            var customer=actor.roles().contains("CUSTOMER")?cb.equal(root.get("customerId"),actor.id()):cb.disjunction();
            var merchant=actor.roles().contains("MERCHANT") && actor.merchantId()!=null?
                cb.equal(root.get("merchantId"),actor.merchantId()):cb.disjunction();
            return cb.or(customer,merchant);
        },PageRequest.of(page,size,Sort.by(sort.endsWith("desc")?Sort.Direction.DESC:Sort.Direction.ASC,"createdAt","id")));
        return new PaymentDtos.Page<>(result.map(PaymentService::view).getContent(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    public Map<String,Object> create(Create request, String key, Actor actor) {
        key(key);
        Payment previous=payments.findByInitiatedByAndIdempotencyKey(actor.id(),key).orElse(null);
        if(previous!=null) return replay(previous,request,actor);
        Context context=pricing.lookup(request.bookingId(),actor);
        Payment payment=tx.execute(s->{
            // Advisory transaction locks serialize a caller's retry key and a booking across instances.
            db.execute("SELECT pg_advisory_xact_lock("+lockNumber(actor.id()+":"+key)+")");
            var replay=payments.findByInitiatedByAndIdempotencyKey(actor.id(),key);
            if(replay.isPresent()) {
                if(!replay.get().getBookingId().equals(request.bookingId())) throw PaymentProblem.conflict("Idempotency key belongs to another booking.");
                return replay.get();
            }
            db.execute("SELECT pg_advisory_xact_lock("+lockNumber("booking:"+request.bookingId())+")");
            if(!db.queryForList("SELECT payment_id FROM payment_settlement.payment_booking_claims WHERE booking_id=?",request.bookingId()).isEmpty())
                throw PaymentProblem.conflict("A payment already exists for this booking. Inspect it before starting another payment.");
            Payment p=new Payment();
            p.setBookingId(request.bookingId()); p.setAmount(context.amount()); p.setCurrency(context.currency());
            p.setCustomerId(context.customerId()); p.setMerchantId(context.merchantId()); p.setInitiatedBy(actor.id());
            p.setIdempotencyKey(key); p.setPricingSnapshotId(context.pricingSnapshotId()); p.setBookingChannel(context.channel());
            p.setPayerJson(json.writeValueAsString(context.payer())); p.setStatus(PaymentStatus.PENDING);
            p.setCreatedAt(now()); p.setUpdatedAt(now()); p.setReconcileAfter(now().plusMinutes(1));
            payments.saveAndFlush(p);
            // Validate provider configuration before committing a pending payment.
            provider.checkout(p,context.payer());
            db.update("INSERT INTO payment_settlement.payment_booking_claims(booking_id,payment_id) VALUES (?,?)",p.getBookingId(),p.getId());
            return p;
        });
        return replay(payment,request,actor);
    }
    private Map<String,Object> replay(Payment p, Create request, Actor actor) {
        if(!p.getBookingId().equals(request.bookingId())) throw PaymentProblem.conflict("Idempotency key belongs to another booking.");
        if("DIRECT_WEBSITE".equals(p.getBookingChannel()) && !actor.admin()) throw PaymentProblem.forbidden();
        actor.requireOwner(p.getCustomerId(),p.getMerchantId());
        Map<String,Object> body=new LinkedHashMap<>(json.convertValue(view(p),new tools.jackson.core.type.TypeReference<Map<String,Object>>() {}));
        if(p.getStatus()==PaymentStatus.PENDING) body.put("checkout",checkout(p.getId(),actor));
        return body;
    }
    public Checkout checkout(UUID id, Actor actor) {
        Payment p=required(id,false); actor.requireOwner(p.getCustomerId(),p.getMerchantId());
        if(p.getStatus()!=PaymentStatus.PENDING || p.getPayerJson()==null) throw PaymentProblem.conflict("Payment is not awaiting checkout.");
        return provider.checkout(p,json.readValue(p.getPayerJson(),Payer.class));
    }
    private static long lockNumber(String value) {
        return java.nio.ByteBuffer.wrap(java.util.HexFormat.of().parseHex(
            com.swiftdrop.payment.onlinepayment.provider.PayHereProvider.digest("SHA-256",value))).getLong();
    }
    public static View view(Payment p) {
        return new View(p.getId(),p.getBookingId(),p.getAmount(),p.getCurrency(),p.getStatus(),p.getRefundedAmount(),
            "PAYHERE",p.getProviderReference(),p.getCreatedAt().toInstant(ZoneOffset.UTC),p.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }
    public void callback(Map<String,String> fields) {
        var c=provider.verify(fields);
        tx.executeWithoutResult(s->{
            Payment p=required(c.orderId(),true);
            if(p.getAmount().compareTo(c.amount())!=0 || !p.getCurrency().equals(c.currency()))
                throw new PaymentProblem(400,"INVALID_CALLBACK","Payment details do not match.");
            int inserted=db.update("""
                INSERT INTO payment_settlement.payment_callbacks(id,fingerprint,payment_id,provider_reference,outcome,received_at)
                VALUES (?,?,?,?,?,?) ON CONFLICT(fingerprint) DO NOTHING
                """,UUID.randomUUID(),c.fingerprint(),p.getId(),c.reference(),"RECEIVED",now());
            if(inserted==0) return;
            String outcome="IGNORED";
            if(c.status()==2 && p.getStatus()==PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.SUCCESS); p.setProviderReference(c.reference()); outcome="APPLIED";
                event(p,"payment.succeeded",Map.of());
            } else if((c.status()==-1 || c.status()==-2) && p.getStatus()==PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.FAILED); outcome="APPLIED"; event(p,"payment.failed",Map.of());
            } else if(c.status()==-3 && p.getStatus()!=PaymentStatus.CHARGEBACKED) {
                // Chargebacks are never interpreted as merchant-authorized refunds.
                p.setStatus(PaymentStatus.CHARGEBACKED); outcome="APPLIED"; event(p,"payment.chargebacked",Map.of());
            } else if(c.status()!=0) outcome="CONFLICT_OR_REPLAY";
            p.setUpdatedAt(now());
            db.update("UPDATE payment_settlement.payment_callbacks SET outcome=? WHERE fingerprint=?",outcome,c.fingerprint());
        });
    }
    private void event(Payment p, String type, Map<String,Object> extra) {
        UUID id=UUID.randomUUID(); Map<String,Object> data=new LinkedHashMap<>();
        data.put("paymentId",p.getId()); data.put("bookingId",p.getBookingId()); data.put("amount",p.getAmount());
        data.put("currency",p.getCurrency()); data.put("status",p.getStatus()); data.put("refundedAmount",p.getRefundedAmount());
        if(p.getCustomerId()!=null) data.put("customerId",p.getCustomerId());
        if(p.getMerchantId()!=null) data.put("merchantId",p.getMerchantId()); data.putAll(extra);
        String payload=json.writeValueAsString(Map.of("eventId",id,"eventType",type,"version",1,
            "occurredAt",Instant.now().toString(),"producer","payment-settlement-service","data",data));
        db.update("INSERT INTO payment_settlement.payment_outbox(id,event_type,payload,created_at,next_attempt_at) VALUES (?,?,?,?,?)",
            id,type,payload,now(),now());
    }
    public Refund refund(UUID id, RefundRequest request, String key, Actor actor) {
        actor.requireAdmin(); key(key);
        if(request.amount()==null || request.amount().signum()<=0 || request.amount().scale()>2
            || request.reason()==null || request.reason().isBlank() || request.reason().length()>500)
            throw new PaymentProblem(400,"VALIDATION_FAILED","A positive decimal amount and reason are required.");
        UUID refundId=tx.execute(s->{
            Payment p=required(id,true);
            db.execute("SELECT pg_advisory_xact_lock("+lockNumber("refund:"+actor.id()+":"+key)+")");
            var existing=db.queryForList("SELECT * FROM payment_settlement.payment_transactions WHERE actor_id=? AND idempotency_key=?",actor.id(),key);
            if(!existing.isEmpty()) {
                var r=existing.getFirst();
                if(!id.equals(r.get("payment_id")) || request.amount().compareTo((BigDecimal)r.get("amount"))!=0 || !request.reason().equals(r.get("reason")))
                    throw PaymentProblem.conflict("Idempotency key belongs to a different refund.");
                return (UUID)r.get("id");
            }
            if(!Set.of(PaymentStatus.SUCCESS,PaymentStatus.PARTIALLY_REFUNDED).contains(p.getStatus()) || p.getProviderReference()==null)
                throw PaymentProblem.conflict("Payment is not eligible for refund.");
            if(p.getReservedAmount().signum()!=0) throw PaymentProblem.conflict("A refund is already pending reconciliation.");
            if(request.amount().compareTo(p.getAmount().subtract(p.getRefundedAmount()))>0)
                throw PaymentProblem.conflict("Refund exceeds the remaining balance.");
            UUID rid=UUID.randomUUID();
            p.setReservedAmount(request.amount()); p.setUpdatedAt(now());
            db.update("""
                INSERT INTO payment_settlement.payment_transactions(id,payment_id,actor_id,idempotency_key,amount,reason,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'PENDING',?,?)
                """,rid,id,actor.id(),key,request.amount(),request.reason(),now(),now());
            return rid;
        });
        submitRefund(refundId);
        return refundView(refundId,id,actor);
    }
    public Refund refundView(UUID rid, UUID paymentId, Actor actor) {
        get(paymentId,actor);
        var rows=db.queryForList("SELECT r.*,p.currency FROM payment_settlement.payment_transactions r JOIN payment_settlement.payments p ON p.id=r.payment_id WHERE r.id=? AND p.id=?",rid,paymentId);
        if(rows.isEmpty()) throw new PaymentProblem(404,"NOT_FOUND","Refund was not found.");
        var r=rows.getFirst();
        return new Refund(rid,paymentId,(BigDecimal)r.get("amount"),(String)r.get("currency"),(String)r.get("status"),
            ((Timestamp)r.get("created_at")).toLocalDateTime().toInstant(ZoneOffset.UTC),((Timestamp)r.get("updated_at")).toLocalDateTime().toInstant(ZoneOffset.UTC));
    }
    public void submitRefund(UUID rid) {
        // Claim once before network I/O. A crash afterwards leaves an uncertain attempt, never a blind resend.
        Map<String,Object> r=tx.execute(s->{
            var rows=db.queryForList("SELECT * FROM payment_settlement.payment_transactions WHERE id=? FOR UPDATE",rid);
            if(rows.isEmpty()) return null;
            var row=rows.getFirst();
            if(!"PENDING".equals(row.get("status")) || row.get("submitted_at")!=null) return null;
            db.update("UPDATE payment_settlement.payment_transactions SET submitted_at=?,updated_at=? WHERE id=?",now(),now(),rid);
            return row;
        });
        if(r==null) return;
        Payment p=required((UUID)r.get("payment_id"),false);
        PaymentProvider.RefundResult result;
        try {
            var matches=provider.retrieve(p.getId());
            boolean verified=matches.size()==1 && matches.getFirst().reference().equals(p.getProviderReference())
                && matches.getFirst().orderId().equals(p.getId().toString()) && matches.getFirst().amount().compareTo(p.getAmount())==0
                && matches.getFirst().currency().equals(p.getCurrency()) && "RECEIVED".equals(matches.getFirst().status());
            if(!verified) return;
            result=provider.refund(p.getProviderReference(),(BigDecimal)r.get("amount"),(String)r.get("reason"));
        } catch(RuntimeException e) { return; }
        tx.executeWithoutResult(s->{
            Payment locked=required(p.getId(),true);
            if("SUCCESS".equals(result.outcome())) {
                int changed=db.update("UPDATE payment_settlement.payment_transactions SET status='SUCCESS',provider_reference=?,updated_at=? WHERE id=? AND status='PENDING'",
                    result.reference(),now(),rid);
                if(changed==1) {
                    BigDecimal amount=(BigDecimal)r.get("amount");
                    locked.setRefundedAmount(locked.getRefundedAmount().add(amount));
                    locked.setReservedAmount(BigDecimal.ZERO); locked.setUpdatedAt(now());
                    if(locked.getStatus()!=PaymentStatus.CHARGEBACKED)
                        locked.setStatus(locked.getRefundedAmount().compareTo(locked.getAmount())==0?PaymentStatus.REFUNDED:PaymentStatus.PARTIALLY_REFUNDED);
                    event(locked,"payment.refunded",Map.of("refundId",rid,"refundAmount",amount));
                }
            } else if("FAILED".equals(result.outcome())) {
                db.update("UPDATE payment_settlement.payment_transactions SET status='FAILED',updated_at=? WHERE id=? AND status='PENDING'",now(),rid);
                locked.setReservedAmount(BigDecimal.ZERO); locked.setUpdatedAt(now());
            } else if(result.reference()!=null && !result.reference().isBlank())
                db.update("UPDATE payment_settlement.payment_transactions SET provider_reference=?,updated_at=? WHERE id=?",result.reference(),now(),rid);
        });
    }
    public void reconcile(UUID id) {
        Payment snapshot=required(id,false);
        var remote=provider.retrieve(id);
        // Multiple results need investigation; never silently choose a second charge.
        if(remote.size()!=1) return;
        var r=remote.getFirst();
        if(!r.orderId().equals(id.toString()) || r.amount().compareTo(snapshot.getAmount())!=0 || !r.currency().equals(snapshot.getCurrency())) return;
        tx.executeWithoutResult(s->{
            Payment p=required(id,true);
            if(p.getProviderReference()!=null && !p.getProviderReference().equals(r.reference())) return;
            if("RECEIVED".equals(r.status()) && Set.of(PaymentStatus.PENDING,PaymentStatus.FAILED).contains(p.getStatus())) {
                p.setProviderReference(r.reference()); p.setStatus(PaymentStatus.SUCCESS); p.setUpdatedAt(now()); event(p,"payment.succeeded",Map.of());
            } else if("CHARGEBACKED".equals(r.status()) && p.getStatus()!=PaymentStatus.CHARGEBACKED) {
                p.setStatus(PaymentStatus.CHARGEBACKED); p.setUpdatedAt(now()); event(p,"payment.chargebacked",Map.of());
            }
            // Only a whole-payment refund can be proven by the documented aggregate retrieval status.
            if("REFUNDED".equals(r.status()) && p.getReservedAmount().compareTo(p.getAmount().subtract(p.getRefundedAmount()))==0
                && p.getReservedAmount().signum()>0 && p.getStatus()!=PaymentStatus.CHARGEBACKED) {
                var refunds=db.queryForList("SELECT id,amount FROM payment_settlement.payment_transactions WHERE payment_id=? AND status='PENDING'",id);
                if(refunds.size()==1) {
                    UUID rid=(UUID)refunds.getFirst().get("id");
                    db.update("UPDATE payment_settlement.payment_transactions SET status='SUCCESS',updated_at=? WHERE id=?",now(),rid);
                    p.setRefundedAmount(p.getAmount()); p.setReservedAmount(BigDecimal.ZERO); p.setStatus(PaymentStatus.REFUNDED); p.setUpdatedAt(now());
                    event(p,"payment.refunded",Map.of("refundId",rid,"refundAmount",refunds.getFirst().get("amount")));
                }
            }
        });
    }
}
