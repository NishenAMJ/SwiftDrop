package com.swiftdrop.identity.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.swiftdrop.identity.ratelimit.RateLimitedException;

/**
 * Turns every error into Problem Details with a SwiftDrop {@code code}. Unexpected errors are
 * logged here and returned as a generic 500, never with internal details.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail problem = Problems.of(ex.status(), ex.code(), ex.status().getReasonPhrase(), ex.getMessage());
        if (!ex.errors().isEmpty()) {
            problem.setTitle("Validation failed");
            problem.setProperty("errors", ex.errors());
        }
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException ex) {
        return ResponseEntity.status(ex.status())
            .header(HttpHeaders.RETRY_AFTER, Long.toString(ex.retryAfterSeconds()))
            .body(Problems.of(ex.status(), ex.code(), ex.status().getReasonPhrase(), ex.getMessage()));
    }

    /** Two people changed the same record at the same moment (for example two dispatchers deciding). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrentChange(ObjectOptimisticLockingFailureException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Problems.of(status, "CONFLICT", status.getReasonPhrase(),
                "Someone else changed this at the same time. Reload it and try again."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status)
            .body(Problems.of(status, "INTERNAL_ERROR", status.getReasonPhrase(), "Something went wrong. Please try again."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> errors.add(new ApiException.FieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
            .forEach(e -> errors.add(new ApiException.FieldError(e.getObjectName(), e.getDefaultMessage())));

        ProblemDetail problem = Problems.of(status, "VALIDATION_FAILED", "Validation failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Adds the SwiftDrop code and type to the Problem Details Spring builds for its own errors. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Map<String, Object> properties = problem.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                Problems.decorate(problem, Problems.codeFor(statusCode), problem.getTitle());
            }
        }
        return response;
    }
}
