package com.flowerconnect.controller.exception;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.security.dto.ErrorResponse;
import com.flowerconnect.vendor.security.VendorNotApprovedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String GENERIC_AUTH_MESSAGE = "Invalid email or password";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GlobalExceptionHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", GENERIC_AUTH_MESSAGE);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        HttpStatus status = statusForErrorCode(ex.getErrorCode());
        return buildErrorResponse(status, status.getReasonPhrase(), ex.getMessage(), ex.getErrorCode(), null);
    }

    /**
     * Vendor approval gating (plan task 2.7). The exception is raised while a
     * method-security rule is being evaluated, so it reaches this advice through
     * the normal MVC exception path rather than through Spring Security's
     * {@code AccessDeniedHandler}. Rendering it here keeps the response shape and
     * the {@code code} field identical to every other business error.
     */
    @ExceptionHandler(VendorNotApprovedException.class)
    public ResponseEntity<ErrorResponse> handleVendorNotApproved(VendorNotApprovedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(),
                ErrorCode.VENDOR_NOT_APPROVED, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request",
                "Validation failed: " + ex.getMessage(), ErrorCode.VALIDATION_FAILED, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                "A resource with this identifier already exists", ErrorCode.CONFLICT, null);
    }

    /**
     * Raised by the servlet container before the request reaches a controller,
     * when the multipart body exceeds {@code spring.servlet.multipart.max-file-size}
     * (plan task 3.8). The service enforces the same ceiling on the part it
     * receives; this override exists so the container-level refusal has the same
     * envelope and the same {@code PAYLOAD_TOO_LARGE} code as the service-level
     * one. Overridden rather than added as a new {@code @ExceptionHandler} because
     * the parent class already declares this exception — a second handler for it
     * is ambiguous and fails the context at startup.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpStatus.PAYLOAD_TOO_LARGE.value())
                .error("Payload Too Large")
                .errorCode(ErrorCode.PAYLOAD_TOO_LARGE)
                .message("Uploaded file is larger than the maximum allowed size")
                .build();
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * A required multipart part was absent — an upload request with no
     * {@code file}. Overridden rather than added as a new handler because the
     * parent class already declares one for this exception, and a second
     * {@code @ExceptionHandler} for it is ambiguous at context startup. The
     * parent's default renders a {@code ProblemDetail}; this keeps the shared
     * {@code ErrorResponse} envelope instead.
     */
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(
            MissingServletRequestPartException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .message("File part is required")
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred", null, null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .message("Validation failed")
                .validation(errors)
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Bad Request")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .message("Request body is required or malformed")
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    private static HttpStatus statusForErrorCode(ErrorCode errorCode) {
        return switch (errorCode) {
            case VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INSUFFICIENT_STOCK -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case ACCOUNT_SUSPENDED -> HttpStatus.FORBIDDEN;
            case VENDOR_NOT_APPROVED -> HttpStatus.FORBIDDEN;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.REQUEST_ENTITY_TOO_LARGE;
        };
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message) {
        return buildErrorResponse(status, error, message, null, null);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String error, String message,
                                                             ErrorCode errorCode, Map<String, String> validation) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(status.value())
                .error(error)
                .errorCode(errorCode)
                .message(message)
                .validation(validation)
                .build();
        return new ResponseEntity<>(response, status);
    }
}
