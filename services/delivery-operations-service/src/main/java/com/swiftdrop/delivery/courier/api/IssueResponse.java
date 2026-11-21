package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.DeliveryIssue;
import com.swiftdrop.delivery.courier.domain.IssueStatus;
import com.swiftdrop.delivery.courier.domain.IssueType;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID courierId,
        String courierName,
        UUID assignmentId,
        String parcelReference,
        IssueType issueType,
        String description,
        IssueStatus status,
        Instant reportedAt,
        String reviewedBy,
        String resolutionNotes,
        Instant reviewedAt
) {
    public static IssueResponse from(DeliveryIssue issue) {
        return new IssueResponse(issue.getId(), issue.getCourier().getId(), issue.getCourier().getFullName(),
                issue.getAssignment() == null ? null : issue.getAssignment().getId(), issue.getParcelReference(),
                issue.getIssueType(), issue.getDescription(), issue.getStatus(), issue.getReportedAt(),
                issue.getReviewedBy(), issue.getResolutionNotes(), issue.getReviewedAt());
    }
}
