package com.swiftdrop.delivery.courier.proof;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Decides whether an uploaded proof file is acceptable: not empty, within the size limit, and really a JPEG, PNG
 * or PDF. The type is read from the file's first bytes. The Content-Type header and the file name are chosen by
 * the caller, so they are never trusted: a script renamed to photo.jpg is refused.
 */
@Component
public class ProofFileValidator {

    static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "application/pdf", "pdf");

    private final long maxBytes;

    public ProofFileValidator(@Value("${delivery.proof.max-bytes:5242880}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    public ValidatedProofFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProofFileException(HttpStatus.BAD_REQUEST, ProofFileException.FILE_EMPTY,
                    "The proof file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException unreadable) {
            throw new ProofFileException(HttpStatus.BAD_REQUEST, ProofFileException.FILE_UNREADABLE,
                    "The proof file could not be read.");
        }
        if (bytes.length > maxBytes) {
            throw tooLarge();
        }
        var type = sniff(bytes);
        if (type == null) {
            throw new ProofFileException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ProofFileException.UNSUPPORTED_FILE_TYPE,
                    "Only JPEG, PNG and PDF files are accepted.");
        }
        return new ValidatedProofFile(bytes, type, displayName(file.getOriginalFilename(), type));
    }

    /** The real type from the leading bytes, or null if it is none of the allowed ones. */
    static String sniff(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (startsWith(bytes, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        return null;
    }

    /** A safe name to show and download: no folders, no odd characters, and an extension that matches the type. */
    static String displayName(String original, String contentType) {
        var extension = EXTENSIONS.get(contentType);
        var name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        var dot = name.lastIndexOf('.');
        var stem = dot > 0 ? name.substring(0, dot) : name;
        stem = stem.replaceAll("[^A-Za-z0-9 _()-]", "_").strip();
        if (stem.length() > 80) {
            stem = stem.substring(0, 80);
        }
        if (stem.isBlank() || stem.chars().allMatch(c -> c == '_')) {
            stem = "proof";
        }
        return stem + "." + extension;
    }

    private ProofFileException tooLarge() {
        return new ProofFileException(HttpStatus.PAYLOAD_TOO_LARGE, ProofFileException.FILE_TOO_LARGE,
                "The proof file is larger than " + (maxBytes / (1024 * 1024)) + " MB.");
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (var i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
