package com.swiftdrop.payment.onlinepayment.service;

import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name="payment.workers.enabled",havingValue="true",matchIfMissing=true)
public class PaymentRecovery {
    private final JdbcTemplate db;
    private final PaymentService service;
    private final TransactionTemplate tx;
    public PaymentRecovery(JdbcTemplate db,PaymentService service,TransactionTemplate tx) { this.db=db; this.service=service; this.tx=tx; }
    @Scheduled(fixedDelayString="${payment.workers.recovery-delay-ms:10000}")
    public void recover() {
        // Only attempts never claimed for submission can be sent by this worker.
        var unsent=db.queryForList("SELECT id FROM payment_settlement.payment_transactions WHERE status='PENDING' AND submitted_at IS NULL ORDER BY created_at LIMIT 1",UUID.class);
        for(UUID rid:unsent) service.submitRefund(rid);
        List<UUID> ids=tx.execute(s->db.queryForList("""
            UPDATE payment_settlement.payments SET reconcile_after=timezone('UTC',now())+interval '5 minutes'
            WHERE id IN (SELECT id FROM payment_settlement.payments WHERE
              (status IN ('PENDING','FAILED') OR reserved_amount>0) AND reconcile_after<=timezone('UTC',now())
              ORDER BY reconcile_after LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id
            """,UUID.class));
        for(UUID id:ids) {
            try { service.reconcile(id); }
            catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payment reconciliation deferred paymentId={}",id); }
        }
    }
}
