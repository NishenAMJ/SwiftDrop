package com.swiftdrop.tracking.common;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
        ProblemDetail problem = Problems.of(
                exception.status(),
                exception.code(),
                exception.status().getReasonPhrase(),
                exception.getMessage());
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Problems.of(
                status,
                "VALIDATION_FAILED",
                "Validation failed",
                "One or more fields are invalid."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
        log.error("Unhandled tracking API error", exception);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Problems.of(
                status,
                "INTERNAL_ERROR",
                status.getReasonPhrase(),
                "Something went wrong. Please try again."));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(
                exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Map<String, Object> properties = problem.getProperties();
            if (properties == null || !properties.containsKey("code")) {
                Problems.decorate(problem, Problems.codeFor(statusCode), problem.getTitle());
            }
        }
        return response;
    }
}
