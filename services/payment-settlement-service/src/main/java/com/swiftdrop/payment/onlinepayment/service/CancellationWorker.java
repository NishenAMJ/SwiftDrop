package com.swiftdrop.payment.onlinepayment.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="payment.workers.enabled",havingValue="true",matchIfMissing=true)
public class CancellationWorker {
    private final CancellationService service;
    public CancellationWorker(CancellationService service) { this.service=service; }
    @Scheduled(fixedDelayString="${payment.workers.cancellation-delay-ms:1000}")
    public void run() { service.processNext(); }
}
