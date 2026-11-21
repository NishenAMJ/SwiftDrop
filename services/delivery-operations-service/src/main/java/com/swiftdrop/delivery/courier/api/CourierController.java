package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.CourierStatus;
import com.swiftdrop.delivery.courier.security.CourierAccess;
import com.swiftdrop.delivery.courier.service.CourierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
// Not /api/v1/couriers: the gateway routes that prefix to identity-merchant-service (applications, approvals).
@RequestMapping("/api/v1/courier-profiles")
public class CourierController {

    private final CourierService courierService;
    private final CourierAccess access;

    public CourierController(CourierService courierService, CourierAccess access) {
        this.courierService = courierService;
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourierResponse create(@Valid @RequestBody CreateCourierRequest request) {
        return courierService.create(request);
    }

    @GetMapping
    public Page<CourierResponse> list(@RequestParam(required = false) CourierStatus status,
                                      @RequestParam(required = false) UUID categoryId,
                                      @PageableDefault(size = 20, sort = "fullName") Pageable pageable) {
        return courierService.list(status, categoryId, pageable);
    }

    @GetMapping("/by-identity/{identityUserId}")
    public CourierResponse getByIdentityUser(@PathVariable UUID identityUserId, @AuthenticationPrincipal Jwt jwt) {
        access.requireSelfOrDispatcher(jwt, identityUserId);
        return courierService.getByIdentityUser(identityUserId);
    }

    @GetMapping("/{courierId}")
    public CourierResponse get(@PathVariable UUID courierId) {
        return courierService.get(courierId);
    }

    @PutMapping("/{courierId}")
    public CourierResponse update(@PathVariable UUID courierId, @Valid @RequestBody UpdateCourierRequest request) {
        return courierService.update(courierId, request);
    }

    @PatchMapping("/{courierId}/status")
    public CourierResponse changeStatus(@PathVariable UUID courierId,
                                        @Valid @RequestBody CourierStatusRequest request) {
        return courierService.changeStatus(courierId, request.status());
    }
}
