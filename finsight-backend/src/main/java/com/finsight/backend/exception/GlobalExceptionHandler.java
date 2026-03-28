package com.finsight.backend.exception;

import com.finsight.backend.util.RuntimeErrorLogger;
import com.finsight.backend.util.RuntimeErrorLogger.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, WebRequest req) {
        log.warn("Resource not found: {}", ex.getMessage());
        RuntimeErrorLogger.log(Module.GENERAL, "ResourceNotFoundException", ex,
            Map.of("request", req.getDescription(false)), "Resource to exist", "Not found");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, fe ->
                fe.getDefaultMessage() == null ? "Invalid value" : fe.getDefaultMessage(),
                (a, b) -> a));
        log.warn("Validation failed: {}", errors);
        RuntimeErrorLogger.logValidation(Module.GENERAL, "ValidationFailed",
            Map.of("request", req.getDescription(false), "fields", errors.toString()),
            "Valid request body", errors.toString());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setType(URI.create("/errors/validation"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("fieldErrors", errors);
        return pd;
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex, WebRequest req) {
        log.warn("Business rule violation: {}", ex.getMessage());
        RuntimeErrorLogger.log(Module.GENERAL, "BusinessException", ex,
            Map.of("request", req.getDescription(false)), null, ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("/errors/business"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(com.google.api.client.googleapis.json.GoogleJsonResponseException.class)
    public ProblemDetail handleGoogleApi(com.google.api.client.googleapis.json.GoogleJsonResponseException ex, WebRequest req) {
        log.error("Google API Error: {}", ex.getDetails() != null ? ex.getDetails() : ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, 
            "Google API Error: " + (ex.getDetails() != null ? ex.getDetails().getMessage() : ex.getMessage()));
        pd.setType(URI.create("/errors/google-api"));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, WebRequest req) {
        log.error("Unhandled exception", ex);
        RuntimeErrorLogger.log(Module.GENERAL, ex.getClass().getSimpleName(), ex,
            Map.of("request", req.getDescription(false)), null, ex.getMessage());
        
        String msg = ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, msg);
        pd.setType(URI.create("/errors/server-error"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
