package com.swiftdrop.communication.common;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class Problems {

    private static final String TYPE_BASE = "https://swiftdrop.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        decorate(problem, code, title);
        return problem;
    }

    public static ProblemDetail badRequest(String code, String detail) {
        return of(HttpStatus.BAD_REQUEST, code, "Bad Request", detail);
    }

    public static ProblemDetail notFound(String code, String detail) {
        return of(HttpStatus.NOT_FOUND, code, "Not Found", detail);
    }

    public static ProblemDetail forbidden(String code, String detail) {
        return of(HttpStatus.FORBIDDEN, code, "Forbidden", detail);
    }

    public static void decorate(ProblemDetail problem, String code, String title) {
        problem.setType(typeFor(code));
        problem.setTitle(title);
        problem.setProperty("code", code);
    }

    public static URI typeFor(String code) {
        return URI.create(TYPE_BASE + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

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
