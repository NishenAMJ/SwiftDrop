package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.delivery.courier.api.AssignParcelRequest;
import com.swiftdrop.delivery.courier.api.AssignmentResponse;
import com.swiftdrop.delivery.courier.api.AttemptResponse;
import com.swiftdrop.delivery.courier.api.ProofResponse;
import com.swiftdrop.delivery.courier.api.RecordAttemptRequest;
import com.swiftdrop.delivery.courier.api.RecordProofRequest;
import com.swiftdrop.delivery.courier.api.StatusChangeResponse;
import com.swiftdrop.delivery.courier.api.UpdateAssignmentStatusRequest;
import com.swiftdrop.delivery.courier.domain.AssignmentStatus;
import com.swiftdrop.delivery.courier.domain.AssignmentStatusChange;
import com.swiftdrop.delivery.courier.domain.CategoryStatus;
import com.swiftdrop.delivery.courier.domain.CourierStatus;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.domain.DeliveryAttempt;
import com.swiftdrop.delivery.courier.domain.ProofOfDelivery;
import com.swiftdrop.delivery.courier.events.DeliveryEventRecorder;
import com.swiftdrop.delivery.courier.parcel.ParcelLookup;
import com.swiftdrop.delivery.courier.proof.ProofFileStorage;
import com.swiftdrop.delivery.courier.proof.ValidatedProofFile;
import com.swiftdrop.delivery.courier.repository.AssignmentStatusChangeRepository;
import com.swiftdrop.delivery.courier.repository.DeliveryAssignmentRepository;
import com.swiftdrop.delivery.courier.repository.DeliveryAttemptRepository;
import com.swiftdrop.delivery.courier.repository.ProofOfDeliveryRepository;
import com.swiftdrop.delivery.courier.security.CurrentActor;
import com.swiftdrop.delivery.exception.AssignmentRuleException;
import com.swiftdrop.delivery.exception.ConflictException;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final AssignmentStatusChangeRepository history;
    private final CurrentActor actor;
    private final ProofFileStorage proofFiles;

    public DeliveryAssignmentService(DeliveryAssignmentRepository assignmentRepository,
                                     DeliveryAttemptRepository attemptRepository,
                                     ProofOfDeliveryRepository proofRepository,
                                     CourierService courierService,
                                     ParcelLookup parcelLookup,
                                     DeliveryEventRecorder events,
                                     AssignmentStatusChangeRepository history,
                                     CurrentActor actor,
                                     ProofFileStorage proofFiles) {
        this.assignmentRepository = assignmentRepository;
        this.attemptRepository = attemptRepository;
        this.proofRepository = proofRepository;
        this.courierService = courierService;
        this.parcelLookup = parcelLookup;
        this.events = events;
        this.history = history;
        this.actor = actor;
        this.proofFiles = proofFiles;
    }

    /**
     * The booking was cancelled in Parcel & Pricing: end any open assignment for it. Safe to repeat. Nothing is
     * published, because Parcel & Pricing already announced the cancellation to everyone who needs it.
     */
    @Transactional
    public int cancelForParcel(UUID parcelId) {
        var open = assignmentRepository.findAllByParcelIdAndStatusIn(parcelId, AssignmentStatus.ACTIVE);
        var now = Instant.now();
        for (var assignment : open) {
            var previous = assignment.getStatus();
            assignment.moveTo(AssignmentStatus.CANCELLED, now);
            audit(assignment, previous, AssignmentStatus.CANCELLED, now);
        }
        return open.size();
    }

    /** The audit trail of an assignment, oldest first. */
    public List<StatusChangeResponse> history(UUID assignmentId) {
        requireAssignment(assignmentId);
        return history.findAllByAssignment_IdOrderByChangedAtAscIdAsc(assignmentId).stream()
                .map(StatusChangeResponse::from).toList();
    }

    /** Records who moved the assignment, from the signed-in user's token, in the same transaction. */
    private void audit(DeliveryAssignment assignment, AssignmentStatus from, AssignmentStatus to, Instant at) {
        var who = actor.get();
        history.save(new AssignmentStatusChange(assignment, from, to, who.id(), who.role(), at));
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
        // A cancelled booking must never be scheduled, even if the cancellation came after it was looked up.
        if (parcel.isCancelled()) {
            throw new AssignmentRuleException(AssignmentRuleException.PARCEL_CANCELLED,
                    "This parcel was cancelled and cannot be assigned: " + reference);
        }
        // The category's weight limit is a hard limit: a parcel over it cannot go with that courier.
        var limit = courier.getCategory().getMaxWeightKg();
        if (limit != null && parcel.weightKg() != null && parcel.weightKg().compareTo(limit) > 0) {
            throw new AssignmentRuleException(AssignmentRuleException.PARCEL_TOO_HEAVY,
                    "The parcel weighs " + parcel.weightKg().stripTrailingZeros().toPlainString() + " kg, over the "
                            + limit.stripTrailingZeros().toPlainString() + " kg limit for category "
                            + courier.getCategory().getCode());
        }
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
        audit(assignment, null, AssignmentStatus.ASSIGNED, now);
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
        if (next == AssignmentStatus.CANCELLED) {
            throw new InvalidOperationException("An assignment is cancelled only when its booking is cancelled");
        }
        var assignment = requireAssignment(assignmentId);
        var occurredAt = request.occurredAt() == null ? Instant.now() : request.occurredAt();
        var previous = assignment.getStatus();
        assignment.moveTo(next, occurredAt);
        events.progressed(assignment, previous, occurredAt);
        audit(assignment, previous, next, occurredAt);
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AttemptResponse recordFailedAttempt(UUID assignmentId, RecordAttemptRequest request) {
        var assignment = requireAssignment(assignmentId);
        var attemptedAt = request.attemptedAt() == null ? Instant.now() : request.attemptedAt();
        var previousStatus = assignment.getStatus();
        assignment.moveTo(AssignmentStatus.DELIVERY_FAILED, attemptedAt);
        var number = (int) attemptRepository.countByParcelReferenceIgnoreCase(assignment.getParcelReference()) + 1;
        var attempt = attemptRepository.save(new DeliveryAttempt(assignment, number, request.reason(),
                cleanNullable(request.notes()), attemptedAt));
        events.failed(assignment, attempt, attemptedAt);
        audit(assignment, previousStatus, AssignmentStatus.DELIVERY_FAILED, attemptedAt);
        return AttemptResponse.from(attempt);
    }

    public List<AttemptResponse> listAttempts(UUID assignmentId) {
        requireAssignment(assignmentId);
        return attemptRepository.findAllByAssignment_IdOrderByAttemptNumberAsc(assignmentId).stream()
                .map(AttemptResponse::from).toList();
    }

    @Transactional
    public ProofResponse recordProof(UUID assignmentId, RecordProofRequest request) {
        return recordProof(assignmentId, request, null);
    }

    /** The bytes and type of an assignment's proof file. Callers must already have checked who may read it. */
    public ProofFile getProofFile(UUID assignmentId) {
        var proof = proofRepository.findByAssignment_Id(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Proof of delivery not found for assignment: " + assignmentId));
        if (!proof.hasFile()) {
            throw new ResourceNotFoundException("No proof file was uploaded for assignment: " + assignmentId);
        }
        return new ProofFile(proofFiles.read(proof.getFileKey()), proof.getFileContentType(), proof.getFileName());
    }

    /** A stored proof file, ready to send back. */
    public record ProofFile(byte[] bytes, String contentType, String name) {
    }

    /** @param file an already validated upload, or null when the courier attached none */
    @Transactional
    public ProofResponse recordProof(UUID assignmentId, RecordProofRequest request, ValidatedProofFile file) {
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
        if (assignment.isCashOnDelivery() && assignment.getMerchantId() == null
                && assignment.getCodAmount().compareTo(request.collectedAmount()) != 0) {
            throw new InvalidOperationException("Collect the full customer delivery fee before completing delivery");
        }
        var deliveredAt = request.deliveredAt() == null ? Instant.now() : request.deliveredAt();
        var previousStatus = assignment.getStatus();
        assignment.moveTo(AssignmentStatus.DELIVERED, deliveredAt);
        var proof = proofRepository.save(new ProofOfDelivery(assignment, request.recipientName().trim(),
                cleanNullable(request.notes()), cleanNullable(request.mediaUrl()), deliveredAt,
                request.collectedAmount()));
        if (file != null) {
            // Written only once the delivery itself is accepted, and removed again if the transaction fails.
            var key = proofFiles.save(file.bytes());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        proofFiles.delete(key);
                    }
                }
            });
            proof.attachFile(key, file.displayName(), file.contentType(), file.bytes().length);
        }
        events.progressed(assignment, AssignmentStatus.OUT_FOR_DELIVERY, deliveredAt);
        audit(assignment, previousStatus, AssignmentStatus.DELIVERED, deliveredAt);
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
