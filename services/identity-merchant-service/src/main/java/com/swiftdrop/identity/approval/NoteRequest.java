package com.swiftdrop.identity.approval;

import jakarta.validation.constraints.Size;

/** Optional body of approve and reactivate. The note is for dispatchers only. */
public record NoteRequest(@Size(max = 500) String note) {
}
