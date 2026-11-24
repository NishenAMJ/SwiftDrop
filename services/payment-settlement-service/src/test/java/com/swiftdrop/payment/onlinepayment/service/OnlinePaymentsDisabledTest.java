package com.swiftdrop.payment.onlinepayment.service;

import com.swiftdrop.payment.onlinepayment.provider.PaymentProvider;
import com.swiftdrop.payment.onlinepayment.repository.PaymentRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OnlinePaymentsDisabledTest {
    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final PricingClient pricing = mock(PricingClient.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final PaymentService service = new PaymentService(repository, db, tx, pricing, provider,
            JsonMapper.builder().build());
    private final Actor dispatcher = new Actor(UUID.randomUUID(), null, Set.of("DISPATCHER"), "test");

    private void disabled(Runnable action) {
        PaymentProblem error = assertThrows(PaymentProblem.class, action::run);
        assertEquals(503, error.status);
        assertEquals("ONLINE_PAYMENTS_DISABLED", error.code);
        verifyNoInteractions(repository, db, tx, pricing, provider);
    }

    @Test void rejectsCreationBeforePricingOrPersistence() {
        disabled(() -> service.create(null, "test-payment-key-123", dispatcher));
    }

    @Test void rejectsCheckoutEvenForAnExistingPayment() {
        disabled(() -> service.checkout(UUID.randomUUID(), dispatcher));
    }

    @Test void rejectsNewRefundsBeforeReservingMoney() {
        disabled(() -> service.refund(UUID.randomUUID(), null, "test-refund-key-123", dispatcher));
    }

    @Test void rejectsRefundRetriesBeforeContactingTheProvider() {
        disabled(() -> service.retryRefund(UUID.randomUUID(), UUID.randomUUID(), true, dispatcher));
    }

    @Test void defersOnlineCancellationWithoutCreatingARefund() {
        disabled(() -> service.reserveCancellation(null));
    }

    @Test void workerDoesNotClaimOrSubmitPendingRefundsWhileDisabled() {
        service.submitRefund(UUID.randomUUID());
        verifyNoInteractions(repository, db, tx, pricing, provider);
    }
}
