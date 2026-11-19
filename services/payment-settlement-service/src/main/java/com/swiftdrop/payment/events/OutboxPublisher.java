package com.swiftdrop.payment.events;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name="payment.workers.enabled",havingValue="true",matchIfMissing=true)
public class OutboxPublisher {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final RabbitTemplate rabbit;
    public OutboxPublisher(JdbcTemplate db,TransactionTemplate tx,RabbitTemplate rabbit) {
        this.db=db; this.tx=tx; this.rabbit=rabbit;
    }
    @Scheduled(fixedDelayString="${payment.workers.outbox-delay-ms:2000}")
    public void publish() {
        UUID token=UUID.randomUUID();
        List<Map<String,Object>> rows=tx.execute(s->db.queryForList("""
            UPDATE payment_settlement.payment_outbox SET locked_until=timezone('UTC',now())+interval '30 seconds',lock_token=?
            WHERE id IN (SELECT id FROM payment_settlement.payment_outbox WHERE published_at IS NULL
              AND next_attempt_at<=timezone('UTC',now()) AND (locked_until IS NULL OR locked_until<timezone('UTC',now()))
              ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING *
            """,token));
        for(var row:rows) {
            UUID id=(UUID)row.get("id");
            try {
                var props=new MessageProperties(); props.setContentType("application/json"); props.setMessageId(id.toString());
                props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                CorrelationData correlation=new CorrelationData(id.toString());
                rabbit.send("swiftdrop.payment",(String)row.get("event_type"),
                    new Message(((String)row.get("payload")).getBytes(StandardCharsets.UTF_8),props),correlation);
                var confirm=correlation.getFuture().get(5,TimeUnit.SECONDS);
                if(!confirm.isAck() || correlation.getReturned()!=null) throw new IllegalStateException("Publication not confirmed");
                db.update("UPDATE payment_settlement.payment_outbox SET published_at=?,locked_until=NULL WHERE id=? AND lock_token=?",
                    LocalDateTime.now(ZoneOffset.UTC),id,token);
            } catch(Exception e) {
                if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                int attempts=((Number)row.get("attempts")).intValue()+1;
                long delay=Math.min(300,1L<<Math.min(attempts,8));
                db.update("UPDATE payment_settlement.payment_outbox SET attempts=?,next_attempt_at=?,locked_until=NULL WHERE id=? AND lock_token=?",
                    attempts,LocalDateTime.now(ZoneOffset.UTC).plusSeconds(delay),id,token);
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Payment event retry scheduled eventId={} attempts={}",id,attempts);
            }
        }
    }
}
