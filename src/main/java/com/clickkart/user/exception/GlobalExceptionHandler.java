// src/main/java/com/clickkart/user/exception/GlobalExceptionHandler.java
package com.clickkart.user.exception;

import com.clickkart.user.constant.LoggerNames;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Central mapping to the standard {@link ApiResponse} envelope (Rule 12) - own copy of the pattern
 * established in Auth Service (Rule 4).
 *
 * <p>No handler here calls {@code printStackTrace()}. Stack traces go through the configured
 * logger or nowhere: the one place a trace is genuinely useful ({@link #handleUnexpected}) logs it
 * at ERROR so it reaches error.log with its correlation id attached, while routine, expected
 * outcomes like a 404 or a validation failure produce a single log line and no trace at all.
 */
@Slf4j(topic = LoggerNames.SECURITY)
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_FIELD_ERROR_MESSAGE = "invalid value";

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("ACCESS_DENIED path={} correlationId={}", request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID));
        return respond(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "You do not have permission to perform this action", request);
    }

    @ExceptionHandler(MissingCorrelationIdException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingCorrelationId(
            MissingCorrelationIdException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ErrorCode.MISSING_CORRELATION_ID, ex.getMessage(), request);
    }

    /** Covers "no such address", "already deleted" and "belongs to another customer" alike - see the exception's Javadoc. */
    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressNotFound(
            AddressNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.ADDRESS_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileNotFound(
            ProfileNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.PROFILE_NOT_FOUND, ex.getMessage(), request);
    }

    /** 409, not 404: the profile exists and is readable, it just cannot be written to any more. */
    @ExceptionHandler(ProfileErasedException.class)
    public ResponseEntity<ApiResponse<Void>> handleProfileErased(
            ProfileErasedException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.PROFILE_ERASED, ex.getMessage(), request);
    }

    /** Carries the reason as display text so support can act on it without reading logs. */
    @ExceptionHandler(ErasureBlockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleErasureBlocked(
            ErasureBlockedException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.ERASURE_BLOCKED, ex.getReason(), request);
    }

    @ExceptionHandler(SellerProfileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleSellerProfileNotFound(
            SellerProfileNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ErrorCode.SELLER_PROFILE_NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateGstinException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateGstin(
            DuplicateGstinException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_GSTIN, ex.getMessage(), request);
    }

    /** Conditional request rules the annotations can't express, e.g. a rejection with no reason. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(AddressLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressLimit(
            AddressLimitExceededException ex, HttpServletRequest request) {
        ErrorDetail errorDetail =
                ErrorDetail.withMetadata(ErrorCode.ADDRESS_LIMIT_EXCEEDED, Map.of("limit", ex.getLimit()));
        return respond(HttpStatus.CONFLICT, errorDetail, ex.getMessage(), request);
    }

    /**
     * Two requests changed the same row concurrently and the {@code @Version} check rejected the
     * loser. 409 rather than 500: nothing is broken, the client simply needs to re-read and retry.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("CONCURRENT_MODIFICATION path={} correlationId={} cause={}",
                request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "This record was changed by another request - please retry", request);
    }

    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleDownstreamUnavailable(
            DownstreamServiceUnavailableException ex, HttpServletRequest request) {
        log.error("DOWNSTREAM_UNAVAILABLE service={} path={} correlationId={} cause={}",
                ex.getServiceName(), request.getRequestURI(), MDC.get(MdcKeys.CORRELATION_ID), ex.toString());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> fieldErrors.put(
                fieldError.getField(),
                fieldError.getDefaultMessage() == null ? DEFAULT_FIELD_ERROR_MESSAGE : fieldError.getDefaultMessage()));
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getName(), DEFAULT_FIELD_ERROR_MESSAGE);
        ErrorDetail errorDetail = ErrorDetail.withFieldErrors(ErrorCode.VALIDATION_FAILED, fieldErrors);
        return respond(HttpStatus.BAD_REQUEST, errorDetail, "One or more fields failed validation", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequestBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request body is missing or malformed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return respond(status, ErrorDetail.of(code), message, request);
    }

    private ResponseEntity<ApiResponse<Void>> respond(
            HttpStatus status, ErrorDetail errorDetail, String message, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<Void> body =
                ApiResponse.error(status.value(), errorDetail, message, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }

    /** Stable, machine-readable codes a UI can switch on - never the free-text {@code message}. */
    private static final class ErrorCode {
        private ErrorCode() {}

        static final String UNAUTHENTICATED = "UNAUTHENTICATED";
        static final String ACCESS_DENIED = "ACCESS_DENIED";
        static final String MISSING_CORRELATION_ID = "MISSING_CORRELATION_ID";
        static final String ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";
        static final String PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";
        static final String SELLER_PROFILE_NOT_FOUND = "SELLER_PROFILE_NOT_FOUND";
        static final String DUPLICATE_GSTIN = "DUPLICATE_GSTIN";
        static final String PROFILE_ERASED = "PROFILE_ERASED";
        static final String ERASURE_BLOCKED = "ERASURE_BLOCKED";
        static final String ADDRESS_LIMIT_EXCEEDED = "ADDRESS_LIMIT_EXCEEDED";
        static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";
        static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        static final String VALIDATION_FAILED = "VALIDATION_FAILED";
        static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    }
}
