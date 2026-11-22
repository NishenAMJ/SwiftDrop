package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.parcel.ParcelServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Error mapping for Parcel & Pricing lookup failures used by courier and warehouse features. */
@RestControllerAdvice
public class CourierApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CourierApiExceptionHandler.class);

    @ExceptionHandler(ParcelServiceUnavailableException.class)
    ProblemDetail handleParcelServiceUnavailable(ParcelServiceUnavailableException exception) {
        // Log the cause; the caller only learns that the parcel could not be checked right now.
        LOG.warn("Parcel lookup failed: {}", exception.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The parcel could not be checked right now. Please try again.");
        problem.setTitle("Parcel service unavailable");
        problem.setType(URI.create("https://api.swiftdrop.local/problems/503"));
        return problem;
    }
}
