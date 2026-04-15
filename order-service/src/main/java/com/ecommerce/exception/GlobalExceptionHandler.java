package com.ecommerce.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex) {
        return buildProblem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IllegalStateException ex) {
        return buildProblem(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError ? ((FieldError) error).getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("urn:ecommerce:validation-error"));
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", errors);
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildProblem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again.");
    }

    private ResponseEntity<ProblemDetail> buildProblem(HttpStatus status, String errorCode, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:ecommerce:" + errorCode.toLowerCase().replace('_', '-')));
        pd.setTitle(errorCode.replace('_', ' '));
        pd.setProperty("errorCode", errorCode);
        pd.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).body(pd);
    }
}
