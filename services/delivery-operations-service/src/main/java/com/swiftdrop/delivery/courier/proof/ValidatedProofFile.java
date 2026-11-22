package com.swiftdrop.delivery.courier.proof;

/** A proof file that passed every check. {@code contentType} comes from the file's own bytes, not the upload header. */
public record ValidatedProofFile(byte[] bytes, String contentType, String displayName) {
}
