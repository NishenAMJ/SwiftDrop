package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.security.CourierAccess;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService assignmentService;
    private final CourierAccess access;

    public DeliveryAssignmentController(DeliveryAssignmentService assignmentService, CourierAccess access) {
        this.assignmentService = assignmentService;
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assign(@Valid @RequestBody AssignParcelRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return assignmentService.assign(request, jwt.getTokenValue());
    }

    @GetMapping
    public Page<AssignmentResponse> list(@RequestParam(required = false) String parcelReference,
                                         @RequestParam(required = false) UUID courierId,
                                         @RequestParam(required = false) AssignmentStatus status,
                                         @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
                                         @AuthenticationPrincipal Jwt jwt) {
        return assignmentService.list(parcelReference, access.scopeToCaller(jwt, courierId), status, pageable);
    }

    @GetMapping("/{assignmentId}")
    public AssignmentResponse get(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.get(assignmentId);
    }

    @PatchMapping("/{assignmentId}/status")
    public AssignmentResponse updateStatus(@PathVariable UUID assignmentId,
                                           @Valid @RequestBody UpdateAssignmentStatusRequest request,
                                           @AuthenticationPrincipal Jwt jwt) {
        access.requireOwner(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.updateStatus(assignmentId, request);
    }

    @PostMapping("/{assignmentId}/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    public AttemptResponse recordFailedAttempt(@PathVariable UUID assignmentId,
                                               @Valid @RequestBody RecordAttemptRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        access.requireOwner(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.recordFailedAttempt(assignmentId, request);
    }

    @GetMapping("/{assignmentId}/attempts")
    public List<AttemptResponse> listAttempts(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.listAttempts(assignmentId);
    }

    @PostMapping("/{assignmentId}/proof")
    @ResponseStatus(HttpStatus.CREATED)
    public ProofResponse recordProof(@PathVariable UUID assignmentId,
                                     @Valid @RequestBody RecordProofRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        access.requireOwner(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.recordProof(assignmentId, request);
    }

    /** Who moved this assignment through each status. Dispatchers only. */
    @GetMapping("/{assignmentId}/history")
    public List<StatusChangeResponse> history(@PathVariable UUID assignmentId) {
        return assignmentService.history(assignmentId);
    }

    @GetMapping("/{assignmentId}/proof")
    public ProofResponse getProof(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.getProof(assignmentId);
    }
}
