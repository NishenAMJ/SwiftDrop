package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.domain.IssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ReportIssueRequest(
        @NotNull UUID courierId,
        UUID assignmentId,
        @Size(max = 50) String parcelReference,
        @NotNull IssueType issueType,
        @NotBlank @Size(max = 1000) String description
) {
}
