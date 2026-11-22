package com.swiftdrop.delivery.courier.api;

import com.swiftdrop.delivery.courier.parcel.ParcelServiceUnavailableException;
import com.swiftdrop.delivery.courier.proof.ProofFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;

/** Error mapping for Parcel & Pricing lookup failures used by courier and warehouse features. */
@RestControllerAdvice
public class CourierApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CourierApiExceptionHandler.class);

    @ExceptionHandler(ProofFileException.class)
    ProblemDetail handleProofFile(ProofFileException exception) {
        return proofFileProblem(exception.getStatus(), exception.getCode(), exception.getMessage());
    }

    /** The server's own upload cap trips before our check runs, so it is mapped to the same answer. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return proofFileProblem(HttpStatus.PAYLOAD_TOO_LARGE, ProofFileException.FILE_TOO_LARGE,
                "The proof file is too large.");
    }

    private static ProblemDetail proofFileProblem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle("Proof file refused");
        problem.setType(URI.create("https://api.swiftdrop.local/problems/" + status.value()));
        problem.setProperty("code", code);
        return problem;
    }

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
