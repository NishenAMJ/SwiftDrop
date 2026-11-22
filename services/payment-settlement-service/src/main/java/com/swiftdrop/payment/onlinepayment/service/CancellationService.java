package com.swiftdrop.payment.onlinepayment.service;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.swiftdrop.payment.onlinepayment.dto.PaymentDtos.Page;

@Service
public class CancellationService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final PaymentService payments;
    public CancellationService(JdbcTemplate db, TransactionTemplate tx, JsonMapper json, PaymentService payments) {
        this.db=db; this.tx=tx; this.json=json; this.payments=payments;
    }
    public void accept(String body) {
        CancellationEvent event=CancellationEvent.parse(body,json);
        tx.executeWithoutResult(s -> {
            db.update("""
                INSERT INTO payment_settlement.cancellation_inbox(event_id,booking_id,payment_id,fingerprint,payload)
                VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
                """,event.eventId(),event.bookingId(),event.paymentId(),event.fingerprint(),body);
            var rows=db.queryForList("""
                SELECT fingerprint FROM payment_settlement.cancellation_inbox
                WHERE event_id=? OR booking_id=? OR payment_id=?
                """,event.eventId(),event.bookingId(),event.paymentId());
            if(rows.size()!=1 || !event.fingerprint().equals(rows.getFirst().get("fingerprint")))
                throw new IllegalArgumentException("Conflicting cancellation replay");
        });
    }
    public void processNext() {
        tx.executeWithoutResult(s -> {
            var rows=db.queryForList("""
                SELECT * FROM payment_settlement.cancellation_inbox
                WHERE (status='PENDING' OR (status='ATTENTION_REQUIRED' AND refund_id IS NOT NULL))
                  AND next_attempt_at<=timezone('UTC',now())
                ORDER BY next_attempt_at,event_id LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
            if(rows.isEmpty()) return;
            var row=rows.getFirst();
            UUID id=(UUID)row.get("event_id");
            db.update("UPDATE payment_settlement.cancellation_inbox SET next_attempt_at=timezone('UTC',now())+interval '30 seconds' WHERE event_id=?",id);
            CancellationEvent event=CancellationEvent.parse((String)row.get("payload"),json);
            if("COD".equals(event.method())) { status(id,"COMPLETED",null); return; }
            UUID refund=(UUID)row.get("refund_id");
            if(refund!=null) {
                var result=db.queryForMap("SELECT r.status,r.submitted_at,p.refunded_amount,p.status AS payment_status FROM payment_settlement.payment_transactions r JOIN payment_settlement.payments p ON p.id=r.payment_id WHERE r.id=?",refund);
                int comparison=((java.math.BigDecimal)result.get("refunded_amount")).compareTo(event.target());
                if ("CHARGEBACKED".equals(result.get("payment_status")) || comparison>0) {
                    status(id,"ATTENTION_REQUIRED","Payment needs dispatcher review after cancellation."); return;
                }
                if (comparison==0) { status(id,"COMPLETED",null); return; }
                String state=(String)result.get("status");
                status(id,"SUCCESS".equals(state) || "FAILED".equals(state)?"ATTENTION_REQUIRED":"PENDING",
                    "SUCCESS".equals(state)?"Confirmed refunds do not yet meet the cancellation target.":
                    "FAILED".equals(state)?"Refund failed; review the payment before another action.":
                    result.get("submitted_at")!=null && "PENDING".equals(state)?"Awaiting verified provider outcome; do not resubmit blindly.":null);
                return;
            }
            try {
                refund=payments.reserveCancellation(event);
                db.update("UPDATE payment_settlement.cancellation_inbox SET refund_id=? WHERE event_id=?",refund,id);
                status(id,refund==null?"COMPLETED":"PENDING",null);
            } catch(PaymentProblem problem) {
                status(id,problem.status==503?"PENDING":"ATTENTION_REQUIRED",problem.getMessage());
            }
        });
    }
    private void status(UUID id,String state,String reason) {
        db.update("UPDATE payment_settlement.cancellation_inbox SET status=?,failure_reason=?,updated_at=timezone('UTC',now()) WHERE event_id=?",state,reason,id);
    }
    // Explicit projection keeps the internal event payload/customer data out of responses.
    private static final String PROJECTION="event_id AS \"eventId\",booking_id AS \"bookingId\",payment_id AS \"paymentId\",status,refund_id AS \"refundId\",failure_reason AS \"failureReason\",created_at AS \"createdAt\",updated_at AS \"updatedAt\"";
    public Page<Map<String,Object>> list(int page,int size,Actor actor) {
        actor.requireAdmin();
        if(page<0 || size<1 || size>100) throw new PaymentProblem(400,"VALIDATION_FAILED","Invalid pagination.");
        long total=db.queryForObject("SELECT count(*) FROM payment_settlement.cancellation_inbox",Long.class);
        var content=db.queryForList("SELECT "+PROJECTION+" FROM payment_settlement.cancellation_inbox ORDER BY created_at DESC,event_id LIMIT ? OFFSET ?",size,(long)page*size);
        return new Page<>(content.stream().map(CancellationService::view).toList(),page,size,total,(int)((total+size-1)/size));
    }
    public Map<String,Object> get(UUID id,Actor actor) {
        actor.requireAdmin();
        var rows=db.queryForList("SELECT "+PROJECTION+" FROM payment_settlement.cancellation_inbox WHERE event_id=?",id);
        if(rows.isEmpty()) throw new PaymentProblem(404,"NOT_FOUND","Cancellation was not found.");
        return view(rows.getFirst());
    }
    private static Map<String,Object> view(Map<String,Object> row) {
        for (String field : List.of("createdAt","updatedAt")) {
            row.put(field,((java.sql.Timestamp)row.get(field)).toLocalDateTime().toInstant(java.time.ZoneOffset.UTC));
        }
        return row;
    }
}
