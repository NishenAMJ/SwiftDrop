package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.IssueStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewIssueRequest(
        @NotNull IssueStatus status,
        @NotBlank @Size(max = 120) String reviewedBy,
        @Size(max = 1000) String resolutionNotes
) {
}
