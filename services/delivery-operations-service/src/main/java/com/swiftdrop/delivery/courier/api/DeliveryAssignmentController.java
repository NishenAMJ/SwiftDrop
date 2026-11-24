package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.proof.ProofFileValidator;
import com.swiftdrop.delivery.courier.security.CourierAccess;
import com.swiftdrop.delivery.courier.service.DeliveryAssignmentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class DeliveryAssignmentController {

    private final DeliveryAssignmentService assignmentService;
    private final CourierAccess access;
    private final ProofFileValidator proofFileValidator;

    public DeliveryAssignmentController(DeliveryAssignmentService assignmentService, CourierAccess access,
                                        ProofFileValidator proofFileValidator) {
        this.assignmentService = assignmentService;
        this.access = access;
        this.proofFileValidator = proofFileValidator;
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

    @PostMapping(value = "/{assignmentId}/proof", consumes = MediaType.APPLICATION_JSON_VALUE)
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

    /**
     * Proof of delivery with an optional uploaded file (JPEG, PNG or PDF). Ownership is checked and the file is
     * validated before anything is saved, so a refused upload never marks the parcel delivered.
     */
    @PostMapping(value = "/{assignmentId}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProofResponse recordProofWithFile(@PathVariable UUID assignmentId,
                                             @Valid @RequestPart("proof") RecordProofRequest request,
                                             @RequestPart(value = "file", required = false) MultipartFile file,
                                             @AuthenticationPrincipal Jwt jwt) {
        access.requireOwner(jwt, assignmentService.courierIdOf(assignmentId));
        var validated = file == null ? null : proofFileValidator.validate(file);
        return assignmentService.recordProof(assignmentId, request, validated);
    }

    /** The uploaded proof file. Only the courier who owns the assignment, or a dispatcher; never public tracking. */
    @GetMapping("/{assignmentId}/proof/file")
    public ResponseEntity<byte[]> getProofFile(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, assignmentService.courierIdOf(assignmentId));
        var file = assignmentService.getProofFile(assignmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                // A file is shown as a file only: no scripts, no embedding, no caching on shared machines.
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore())
                .body(file.bytes());
    }

    @GetMapping("/{assignmentId}/proof")
    public ProofResponse getProof(@PathVariable UUID assignmentId, @AuthenticationPrincipal Jwt jwt) {
        access.requireOwnerOrDispatcher(jwt, assignmentService.courierIdOf(assignmentId));
        return assignmentService.getProof(assignmentId);
    }
}
