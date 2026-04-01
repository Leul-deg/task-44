package com.shiftworks.jobops.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
            .collect(Collectors.toList());
        ApiError apiError = ApiError.builder()
            .code(HttpStatus.BAD_REQUEST.value())
            .message("Validation failed")
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
            .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
            .collect(Collectors.toList());
        ApiError apiError = ApiError.builder()
            .code(HttpStatus.BAD_REQUEST.value())
            .message("Constraint violation")
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .fieldErrors(fieldErrors)
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ApiError apiError = ApiError.builder()
            .code(status.value())
            .message(ex.getMessage())
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .fieldErrors(List.of())
            .build();
        return ResponseEntity.status(status).body(apiError);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiError apiError = ApiError.builder()
            .code(HttpStatus.FORBIDDEN.value())
            .message("Access denied")
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .fieldErrors(List.of())
            .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(apiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        ApiError apiError = ApiError.builder()
            .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .message("Unexpected server error")
            .path(request.getRequestURI())
            .timestamp(Instant.now().toString())
            .fieldErrors(List.of())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    @Value
    public static class FieldErrorDetail {
        String field;
        String message;
    }

    @Value
    @Builder
    public static class ApiError {
        int code;
        String message;
        String timestamp;
        String path;
        List<FieldErrorDetail> fieldErrors;
    }
}
