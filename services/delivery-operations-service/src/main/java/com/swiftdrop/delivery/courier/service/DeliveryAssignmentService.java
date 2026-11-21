package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.AttemptResponse;
import com.swiftdrop.delivery.courier.api.ProofResponse;
import com.swiftdrop.delivery.courier.api.RecordAttemptRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import com.swiftdrop.delivery.courier.domain.CourierStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import com.swiftdrop.delivery.courier.domain.ProofOfDelivery;
import com.swiftdrop.delivery.courier.events.DeliveryEventRecorder;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.repository.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.courier.repository.DeliveryAttemptRepository;
import com.swiftdrop.delivery.courier.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeliveryAssignmentService {

    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryAttemptRepository attemptRepository;
    private final ProofOfDeliveryRepository proofRepository;
    private final CourierService courierService;
    private final ParcelLookup parcelLookup;
    private final DeliveryEventRecorder events;

    public DeliveryAssignmentService(DeliveryAssignmentRepository assignmentRepository,
                                     DeliveryAttemptRepository attemptRepository,
                                     ProofOfDeliveryRepository proofRepository,
                                     CourierService courierService,
                                     ParcelLookup parcelLookup,
                                     DeliveryEventRecorder events) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.proofRepository = proofRepository;
        this.courierService = courierService;
        this.parcelLookup = parcelLookup;
        this.events = events;
    }

    /**
     * @param bearerToken the dispatcher's access token, forwarded to Parcel & Pricing to resolve the parcel
     */
    @Transactional
    public AssignmentResponse assign(AssignParcelRequest request, String bearerToken) {
        var courier = courierService.requireCourier(request.courierId());
        if (courier.getStatus() != CourierStatus.AVAILABLE) {
            throw new InvalidOperationException("Courier is not available: " + courier.getFullName());
        }
        if (courier.getCategory().getStatus() != CategoryStatus.ACTIVE) {
            throw new InvalidOperationException("Courier category is not active: " + courier.getCategory().getCode());
        }
        // The parcel must exist in Parcel & Pricing; its id is what tracking and other services key on.
        var parcel = parcelLookup.find(normalizeReference(request.parcelReference()), bearerToken);
        var reference = normalizeReference(parcel.trackingNumber());
        if (assignmentRepository.existsByParcelReferenceIgnoreCaseAndStatusIn(reference, AssignmentStatus.ACTIVE)) {
            throw new ConflictException("Parcel already has an active assignment: " + reference);
        }
        var now = Instant.now();
        var created = new DeliveryAssignment(parcel.parcelId(), parcel.customerId(),
                reference, courier, request.assignedBy().trim(), cleanNullable(request.notes()), now);
        // Before the first save: these columns are never updated afterwards.
        created.recordContacts(parcel.pickup(), parcel.delivery());
        if (parcel.merchantId() != null) {
            created.forMerchant(parcel.merchantId());
        }
        if (parcel.collectsCash()) {
            created.collectCash(parcel.codAmount(), parcel.pricingSnapshotId(), parcel.currency());
        }
        var assignment = assignmentRepository.save(created);
        events.progressed(assignment, null, now);
        return AssignmentResponse.from(assignment);
    }

    public AssignmentResponse get(UUID assignmentId) {
        return AssignmentResponse.from(requireAssignment(assignmentId));
    }

    public Page<AssignmentResponse> list(String parcelReference, UUID courierId, AssignmentStatus status,
                                         Pageable pageable) {
        if (parcelReference != null && !parcelReference.isBlank()) {
            var reference = normalizeReference(parcelReference);
            // A courier filter must never be dropped, or one courier could read another's assignments.
            var page = courierId != null
                    ? assignmentRepository.findAllByCourier_IdAndParcelReferenceIgnoreCase(courierId, reference, pageable)
                    : assignmentRepository.findAllByParcelReferenceIgnoreCase(reference, pageable);
            return page.map(AssignmentResponse::from);
        }
        if (courierId != null && status != null) {
            return assignmentRepository.findAllByCourier_IdAndStatus(courierId, status, pageable)
                    .map(AssignmentResponse::from);
        }
        if (courierId != null) {
            return assignmentRepository.findAllByCourier_Id(courierId, pageable).map(AssignmentResponse::from);
        }
        if (status != null) {
            return assignmentRepository.findAllByStatus(status, pageable).map(AssignmentResponse::from);
        }
        return assignmentRepository.findAll(pageable).map(AssignmentResponse::from);
    }

    @Transactional
    public AssignmentResponse updateStatus(UUID assignmentId, UpdateAssignmentStatusRequest request) {
        var next = request.status();
        if (next == AssignmentStatus.DELIVERED) {
            throw new InvalidOperationException("Record proof of delivery to mark a parcel as DELIVERED");
        }
        if (next == AssignmentStatus.DELIVERY_FAILED) {
            throw new InvalidOperationException("Record a failed delivery attempt to mark a parcel as DELIVERY_FAILED");
        }
        var assignment = requireAssignment(assignmentId);
        var occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        var previous = assignment.getStatus();
        assignment.moveTo(next, occurredAt);
        events.progressed(assignment, previous, occurredAt);
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AttemptResponse recordFailedAttempt(UUID assignmentId, RecordAttemptRequest request) {
        var assignment = requireAssignment(assignmentId);
        var attemptedAt = request.attemptedAt() == null ? Instant.now() : request.attemptedAt();
        assignment.moveTo(AssignmentStatus.DELIVERY_FAILED, attemptedAt);
        var number = (int) attemptRepository.countByParcelReferenceIgnoreCase(assignment.getParcelReference()) + 1;
        var attempt = attemptRepository.save(new DeliveryAttempt(assignment, number, request.reason(),
                cleanNullable(request.notes()), attemptedAt));
        events.failed(assignment, attempt, attemptedAt);
        return AttemptResponse.from(attempt);
    }

    public List<AttemptResponse> listAttempts(UUID assignmentId) {
        requireAssignment(assignmentId);
        return attemptRepository.findAllByAssignment_IdOrderByAttemptNumberAsc(assignmentId).stream()
                .map(AttemptResponse::from).toList();
    }

    @Transactional
    public ProofResponse recordProof(UUID assignmentId, RecordProofRequest request) {
        var assignment = requireAssignment(assignmentId);
        if (proofRepository.findByAssignment_Id(assignmentId).isPresent()) {
            throw new ConflictException("Proof of delivery already recorded for assignment: " + assignmentId);
        }
        // Cash is collected for Cash on Delivery parcels only, and the courier must say how much.
        if (assignment.isCashOnDelivery() && request.collectedAmount() == null) {
            throw new InvalidOperationException("Enter the cash collected: this parcel is Cash on Delivery");
        }
        if (!assignment.isCashOnDelivery() && request.collectedAmount() != null) {
            throw new InvalidOperationException("This parcel is not Cash on Delivery, so no cash can be recorded");
        }
        var deliveredAt = request.deliveredAt() == null ? Instant.now() : request.deliveredAt();
        assignment.moveTo(AssignmentStatus.DELIVERED, deliveredAt);
        var proof = proofRepository.save(new ProofOfDelivery(assignment, request.recipientName().trim(),
                cleanNullable(request.notes()), cleanNullable(request.mediaUrl()), deliveredAt,
                request.collectedAmount()));
        events.progressed(assignment, AssignmentStatus.OUT_FOR_DELIVERY, deliveredAt);
        if (assignment.isCashOnDelivery()) {
            events.cashCollected(assignment, proof, deliveredAt);
        }
        return ProofResponse.from(proof);
    }

    /** The courier an assignment belongs to; used by controllers for ownership checks. */
    public UUID courierIdOf(UUID assignmentId) {
        return requireAssignment(assignmentId).getCourier().getId();
    }

    public ProofResponse getProof(UUID assignmentId) {
        return proofRepository.findByAssignment_Id(assignmentId).map(ProofResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proof of delivery not found for assignment: " + assignmentId));
    }

    public DeliveryAssignment requireAssignment(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }

    private String normalizeReference(String reference) {
        return reference.trim().toUpperCase(Locale.ROOT);
    }

    private String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
