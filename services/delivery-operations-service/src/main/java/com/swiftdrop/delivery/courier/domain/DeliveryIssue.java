package com.swiftdrop.delivery.courier.domain;

import com.swiftdrop.delivery.exception.InvalidOperationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_issues")
public class DeliveryIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private DeliveryAssignment assignment;

    @Column(name = "parcel_reference", length = 50)
    private String parcelReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 40)
    private IssueType issueType;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueStatus status;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected DeliveryIssue() {
    }

    public DeliveryIssue(Courier courier, DeliveryAssignment assignment, String parcelReference, IssueType issueType,
                         String description, Instant reportedAt) {
        this.courier = courier;
        this.assignment = assignment;
        this.parcelReference = parcelReference;
        this.issueType = issueType;
        this.description = description;
        this.reportedAt = reportedAt;
        this.status = IssueStatus.OPEN;
    }

    public void review(IssueStatus next, String reviewer, String notes, Instant at) {
        boolean allowed = switch (status) {
            case OPEN -> next == IssueStatus.UNDER_REVIEW || next == IssueStatus.RESOLVED || next == IssueStatus.DISMISSED;
            case UNDER_REVIEW -> next == IssueStatus.RESOLVED || next == IssueStatus.DISMISSED;
            case RESOLVED, DISMISSED -> false;
        };
        if (!allowed) {
            throw new InvalidOperationException("Cannot change issue status from " + status + " to " + next);
        }
        status = next;
        reviewedBy = reviewer;
        resolutionNotes = notes;
        reviewedAt = at;
    }

    public UUID getId() { return id; }
    public Courier getCourier() { return courier; }
    public DeliveryAssignment getAssignment() { return assignment; }
    public String getParcelReference() { return parcelReference; }
    public IssueType getIssueType() { return issueType; }
    public String getDescription() { return description; }
    public IssueStatus getStatus() { return status; }
    public Instant getReportedAt() { return reportedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getReviewedAt() { return reviewedAt; }
}
