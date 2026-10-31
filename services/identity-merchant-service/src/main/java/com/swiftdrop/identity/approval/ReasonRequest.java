package com.swiftdrop.identity.approval;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of reject and suspend. The reason is shown to the applicant. */
public record ReasonRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
}
