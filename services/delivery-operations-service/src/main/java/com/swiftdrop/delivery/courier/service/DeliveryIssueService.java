package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.delivery.courier.api.IssueResponse;
import com.swiftdrop.delivery.courier.api.ReportIssueRequest;
import com.swiftdrop.delivery.courier.api.ReviewIssueRequest;
import com.swiftdrop.delivery.courier.domain.DeliveryAssignment;
import com.swiftdrop.delivery.courier.domain.DeliveryIssue;
import com.swiftdrop.delivery.courier.domain.IssueStatus;
import com.swiftdrop.delivery.courier.repository.DeliveryIssueRepository;
import com.swiftdrop.delivery.exception.InvalidOperationException;
import com.swiftdrop.delivery.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DeliveryIssueService {

    private final DeliveryIssueRepository issueRepository;
    private final CourierService courierService;
    private final DeliveryAssignmentService assignmentService;

    public DeliveryIssueService(DeliveryIssueRepository issueRepository, CourierService courierService,
                                DeliveryAssignmentService assignmentService) {
        this.issueRepository = issueRepository;
        this.courierService = courierService;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public IssueResponse report(ReportIssueRequest request) {
        var courier = courierService.requireCourier(request.courierId());
        DeliveryAssignment assignment = null;
        String reference = request.parcelReference() == null || request.parcelReference().isBlank()
                ? null : request.parcelReference().trim().toUpperCase(Locale.ROOT);
        if (request.assignmentId() != null) {
            assignment = assignmentService.requireAssignment(request.assignmentId());
            if (!assignment.getCourier().getId().equals(courier.getId())) {
                throw new InvalidOperationException("Assignment does not belong to this courier");
            }
            reference = assignment.getParcelReference();
        }
        var issue = new DeliveryIssue(courier, assignment, reference, request.issueType(),
                request.description().trim(), Instant.now());
        return IssueResponse.from(issueRepository.save(issue));
    }

    public Page<IssueResponse> list(UUID courierId, IssueStatus status, Pageable pageable) {
        if (courierId != null && status != null) {
            return issueRepository.findAllByCourier_IdAndStatus(courierId, status, pageable).map(IssueResponse::from);
        }
        if (courierId != null) {
            return issueRepository.findAllByCourier_Id(courierId, pageable).map(IssueResponse::from);
        }
        if (status != null) {
            return issueRepository.findAllByStatus(status, pageable).map(IssueResponse::from);
        }
        return issueRepository.findAll(pageable).map(IssueResponse::from);
    }

    public IssueResponse get(UUID id) {
        return IssueResponse.from(requireIssue(id));
    }

    @Transactional
    public IssueResponse review(UUID id, ReviewIssueRequest request) {
        var issue = requireIssue(id);
        if (request.status() == IssueStatus.OPEN) {
            throw new InvalidOperationException("An issue cannot be moved back to OPEN");
        }
        var notes = request.resolutionNotes() == null || request.resolutionNotes().isBlank()
                ? null : request.resolutionNotes().trim();
        issue.review(request.status(), request.reviewedBy().trim(), notes, Instant.now());
        return IssueResponse.from(issue);
    }

    /** The courier who reported an issue; used by controllers for ownership checks. */
    public UUID courierIdOf(UUID issueId) {
        return requireIssue(issueId).getCourier().getId();
    }

    private DeliveryIssue requireIssue(UUID id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery issue not found: " + id));
    }
}
