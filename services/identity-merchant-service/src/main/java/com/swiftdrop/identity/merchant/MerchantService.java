package com.swiftdrop.identity.merchant;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.identity.approval.ApplicationAction;
import com.swiftdrop.identity.approval.ApplicationDecision;
import com.swiftdrop.identity.approval.ApplicationStatus;
import com.swiftdrop.identity.approval.DecisionLog;
import com.swiftdrop.identity.common.ApiException;
import com.swiftdrop.identity.event.EventRecorder;
import com.swiftdrop.identity.user.RoleName;
import com.swiftdrop.identity.user.UserService;

/**
 * Merchant applications and dispatcher decisions. Every change writes the audit trail and a
 * {@code merchant.*} event in the same transaction; approval and suspension also change the
 * user's MERCHANT role.
 */
@Service
public class MerchantService {

    private final MerchantRepository merchants;
    private final UserService userService;
    private final DecisionLog decisionLog;
    private final EventRecorder events;
    private final Clock clock;

    public MerchantService(MerchantRepository merchants, UserService userService, DecisionLog decisionLog,
            EventRecorder events, Clock clock) {
        this.merchants = merchants;
        this.userService = userService;
        this.decisionLog = decisionLog;
        this.events = events;
        this.clock = clock;
    }

    /** Apply, or resubmit after a rejection (same merchant ID). */
    @Transactional
    public MerchantResponse apply(UUID userId, MerchantApplicationRequest request) {
        userService.requireApplicant(userId, RoleName.MERCHANT);
        Instant now = clock.instant();
        Merchant existing = merchants.findByUserId(userId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() != ApplicationStatus.REJECTED) {
                throw alreadyExists();
            }
            existing.replaceDetails(request, now);
            ApplicationStatus previous = existing.apply(ApplicationAction.RESUBMIT, userId, null, now);
            merchants.saveAndFlush(existing);
            record(existing, ApplicationAction.RESUBMIT, previous, null, null, userId, now);
            return MerchantResponse.from(existing);
        }

        Merchant merchant = new Merchant(userId, request, now);
        try {
            merchants.saveAndFlush(merchant);
        }
        catch (DataIntegrityViolationException ex) {
            throw alreadyExists();
        }
        record(merchant, ApplicationAction.SUBMIT, null, null, null, userId, now);
        return MerchantResponse.from(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse mine(UUID userId) {
        return merchants.findByUserId(userId).map(MerchantResponse::from)
            .orElseThrow(() -> ApiException.notFound("You have not applied to become a merchant."));
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(UUID merchantId) {
        return MerchantResponse.from(find(merchantId));
    }

    @Transactional(readOnly = true)
    public Page<MerchantResponse> search(ApplicationStatus status, String text, Pageable pageable) {
        return merchants.findAll(MerchantSearch.matching(status, text), pageable).map(MerchantResponse::from);
    }

    /**
     * A dispatcher's decision: approve, reject, suspend or reactivate.
     *
     * @param reason required for reject and suspend (shown to the merchant)
     * @param note optional internal note for approve and reactivate
     */
    @Transactional
    public MerchantResponse decide(UUID merchantId, ApplicationAction action, UUID dispatcherId, String reason,
            String note) {
        Instant now = clock.instant();
        Merchant merchant = find(merchantId);
        ApplicationStatus previous = merchant.apply(action, dispatcherId, reason, now);
        merchants.saveAndFlush(merchant);

        if (action.grantsRole()) {
            userService.grantRole(merchant.getUserId(), RoleName.MERCHANT);
        }
        else if (action.revokesRole()) {
            userService.revokeRole(merchant.getUserId(), RoleName.MERCHANT);
        }
        record(merchant, action, previous, reason, note, dispatcherId, now);
        return MerchantResponse.from(merchant);
    }

    private Merchant find(UUID merchantId) {
        return merchants.findById(merchantId).orElseThrow(() -> ApiException.notFound("Merchant not found."));
    }

    private void record(Merchant merchant, ApplicationAction action, ApplicationStatus previous, String reason,
            String note, UUID actorId, Instant now) {
        decisionLog.record(ApplicationDecision.Type.MERCHANT, merchant.getId(), action, previous, reason, note,
                actorId, now);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", merchant.getId().toString());
        data.put("userId", merchant.getUserId().toString());
        data.put("businessName", merchant.getBusinessName());
        data.put("status", merchant.getStatus().name());
        if (previous != null) {
            data.put("previousStatus", previous.name());
        }
        if (reason != null) {
            data.put("reason", reason);
        }
        if (action.isDecision()) {
            data.put("decidedBy", actorId.toString());
        }
        events.record(action.eventType("merchant"), data);
    }

    private static ApiException alreadyExists() {
        return new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                "You already have a merchant application. You can only apply again after a rejection.");
    }
}
