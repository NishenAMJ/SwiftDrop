package com.swiftdrop.identity.courier;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.identity.approval.ApplicationAction;
import com.swiftdrop.identity.approval.ApplicationStatus;
import com.swiftdrop.identity.approval.NoteRequest;
import com.swiftdrop.identity.approval.ReasonRequest;
import com.swiftdrop.identity.common.PageResponse;
import com.swiftdrop.identity.common.SortGuard;

/** Couriers tag of the OpenAPI contract. Who may call what is set in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/couriers")
public class CourierController {

    private static final Set<String> SORTABLE = Set.of("createdAt", "submittedAt");

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CourierResponse apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CourierApplicationRequest request) {
        return courierService.apply(userId(jwt), request).forApplicant();
    }

    @GetMapping("/me")
    CourierResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return courierService.mine(userId(jwt)).forApplicant();
    }

    @GetMapping
    PageResponse<CourierResponse> list(@RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) VehicleType vehicleType,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        SortGuard.check(pageable, SORTABLE);
        return PageResponse.of(courierService.search(status, vehicleType, pageable), c -> c);
    }

    @GetMapping("/{userId}")
    CourierResponse get(@PathVariable UUID userId) {
        return courierService.get(userId);
    }

    @PostMapping("/{userId}/approve")
    CourierResponse approve(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) NoteRequest body) {
        return courierService.decide(userId, ApplicationAction.APPROVE, userId(jwt), null, note(body));
    }

    @PostMapping("/{userId}/reject")
    CourierResponse reject(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest body) {
        return courierService.decide(userId, ApplicationAction.REJECT, userId(jwt), body.reason().trim(), null);
    }

    @PostMapping("/{userId}/suspend")
    CourierResponse suspend(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReasonRequest body) {
        return courierService.decide(userId, ApplicationAction.SUSPEND, userId(jwt), body.reason().trim(), null);
    }

    @PostMapping("/{userId}/reactivate")
    CourierResponse reactivate(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) NoteRequest body) {
        return courierService.decide(userId, ApplicationAction.REACTIVATE, userId(jwt), null, note(body));
    }

    private static String note(NoteRequest body) {
        return body == null || body.note() == null || body.note().isBlank() ? null : body.note().trim();
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
