package com.swiftdrop.identity.common;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 Problem Details in the shape described in contracts/README.md:
 * {@code type}, {@code title}, {@code status}, {@code detail}, plus a SwiftDrop {@code code}.
 */
public final class Problems {

    private static final String TYPE_BASE = "https://swiftdrop.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        decorate(problem, code, title);
        return problem;
    }

    public static void decorate(ProblemDetail problem, String code, String title) {
        problem.setType(typeFor(code));
        problem.setTitle(title);
        problem.setProperty("code", code);
    }

    public static URI typeFor(String code) {
        return URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    /** Default code for errors Spring raises itself (wrong method, unknown path, bad JSON...). */
    public static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }
}
