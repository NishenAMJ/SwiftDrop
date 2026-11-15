package com.swiftdrop.payment.onlinepayment.controller;

import com.swiftdrop.payment.onlinepayment.service.PaymentProblem;
import jakarta.servlet.http.*;
import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import tools.jackson.databind.json.JsonMapper;

@RestControllerAdvice
public class ProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final JsonMapper json;
    public ProblemHandler(JsonMapper json) { this.json=json; }
    private ProblemDetail body(int status,String code,String detail,String path) {
        var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail);
        p.setType(URI.create("https://swiftdrop.dev/problems/"+code.toLowerCase(Locale.ROOT).replace('_','-')));
        p.setInstance(URI.create(path)); p.setProperty("code",code); return p;
    }
    @ExceptionHandler(PaymentProblem.class)
    ResponseEntity<ProblemDetail> domain(PaymentProblem e,HttpServletRequest r) {
        return ResponseEntity.status(e.status).body(body(e.status,e.code,e.getMessage(),r.getRequestURI()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
        MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class,MissingRequestHeaderException.class})
    ResponseEntity<ProblemDetail> invalid(Exception e,HttpServletRequest r) {
        var p=body(400,"VALIDATION_FAILED","Request fields are invalid.",r.getRequestURI());
        if(e instanceof MethodArgumentNotValidException v) p.setProperty("errors",v.getBindingResult().getFieldErrors().stream()
            .map(f->Map.of("field",f.getField(),"message",Objects.toString(f.getDefaultMessage(),"Invalid value"))).toList());
        return ResponseEntity.badRequest().body(p);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(Exception e,HttpServletRequest r) {
        return ResponseEntity.status(409).body(body(409,"CONFLICT","Operation conflicts with an existing payment.",r.getRequestURI()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e,HttpServletRequest r) {
        if(e instanceof org.springframework.web.ErrorResponse error)
            return ResponseEntity.status(error.getStatusCode()).body(body(error.getStatusCode().value(),"INVALID_REQUEST","The request could not be processed.",r.getRequestURI()));
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Payment request failed: type={}",e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(body(500,"INTERNAL_ERROR","The payment operation could not be completed.",r.getRequestURI()));
    }
    @Override public void commence(HttpServletRequest r,HttpServletResponse s,AuthenticationException e) throws IOException {
        s.setHeader("WWW-Authenticate","Bearer"); write(r,s,401,"UNAUTHENTICATED","A valid access token is required.");
    }
    @Override public void handle(HttpServletRequest r,HttpServletResponse s,AccessDeniedException e) throws IOException {
        write(r,s,403,"FORBIDDEN","This operation is not permitted.");
    }
    private void write(HttpServletRequest r,HttpServletResponse s,int status,String code,String detail) throws IOException {
        s.setStatus(status); s.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(s.getOutputStream(),body(status,code,detail,r.getRequestURI()));
    }
}
