package com.swiftdrop.identity.courier;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.criteria.Predicate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
 * Courier applications and dispatcher decisions. Same rules as merchants. delivery-operations-service
 * creates its own courier profile when it receives {@code courier.approved}.
 */
@Service
public class CourierService {

    private final CourierRepository couriers;
    private final UserService userService;
    private final DecisionLog decisionLog;
    private final EventRecorder events;
    private final Clock clock;

    public CourierService(CourierRepository couriers, UserService userService, DecisionLog decisionLog,
            EventRecorder events, Clock clock) {
        this.couriers = couriers;
        this.userService = userService;
        this.decisionLog = decisionLog;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CourierResponse apply(UUID userId, CourierApplicationRequest request) {
        userService.requireApplicant(userId, RoleName.COURIER);
        Instant now = clock.instant();
        Courier existing = couriers.findById(userId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() != ApplicationStatus.REJECTED) {
                throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                        "You already have a courier application. You can only apply again after a rejection.");
            }
            existing.replaceDetails(request, now);
            ApplicationStatus previous = existing.apply(ApplicationAction.RESUBMIT, userId, null, now);
            couriers.saveAndFlush(existing);
            record(existing, ApplicationAction.RESUBMIT, previous, null, null, userId, now);
            return CourierResponse.from(existing);
        }

        Courier courier = new Courier(userService.get(userId), request, now);
        try {
            couriers.saveAndFlush(courier);
        }
        catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
                    "You already have a courier application.");
        }
        record(courier, ApplicationAction.SUBMIT, null, null, null, userId, now);
        return CourierResponse.from(courier);
    }

    @Transactional(readOnly = true)
    public CourierResponse mine(UUID userId) {
        return couriers.findById(userId).map(CourierResponse::from)
            .orElseThrow(() -> ApiException.notFound("You have not applied to become a courier."));
    }

    @Transactional(readOnly = true)
    public CourierResponse get(UUID userId) {
        return CourierResponse.from(find(userId));
    }

    @Transactional(readOnly = true)
    public Page<CourierResponse> search(ApplicationStatus status, VehicleType vehicleType, Pageable pageable) {
        Specification<Courier> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (vehicleType != null) {
                predicates.add(cb.equal(root.get("vehicleType"), vehicleType));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return couriers.findAll(spec, pageable).map(CourierResponse::from);
    }

    @Transactional
    public CourierResponse decide(UUID courierUserId, ApplicationAction action, UUID dispatcherId, String reason,
            String note) {
        Instant now = clock.instant();
        Courier courier = find(courierUserId);
        ApplicationStatus previous = courier.apply(action, dispatcherId, reason, now);
        couriers.saveAndFlush(courier);

        if (action.grantsRole()) {
            userService.grantRole(courierUserId, RoleName.COURIER);
        }
        else if (action.revokesRole()) {
            userService.revokeRole(courierUserId, RoleName.COURIER);
        }
        record(courier, action, previous, reason, note, dispatcherId, now);
        return CourierResponse.from(courier);
    }

    private Courier find(UUID userId) {
        return couriers.findById(userId).orElseThrow(() -> ApiException.notFound("Courier application not found."));
    }

    private void record(Courier courier, ApplicationAction action, ApplicationStatus previous, String reason,
            String note, UUID actorId, Instant now) {
        decisionLog.record(ApplicationDecision.Type.COURIER, courier.getUserId(), action, previous, reason, note,
                actorId, now);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", courier.getUserId().toString());
        data.put("fullName", courier.getFullName());
        data.put("vehicleType", courier.getVehicleType().name());
        data.put("status", courier.getStatus().name());
        if (previous != null) {
            data.put("previousStatus", previous.name());
        }
        if (reason != null) {
            data.put("reason", reason);
        }
        if (action.isDecision()) {
            data.put("decidedBy", actorId.toString());
        }
        events.record(action.eventType("courier"), data);
    }
}
