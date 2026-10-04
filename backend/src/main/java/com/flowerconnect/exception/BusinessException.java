package com.flowerconnect.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, message);
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    /**
     * The stock change the caller asked for is not possible against the
     * current quantities: it would drive {@code quantity} below
     * {@code reserved_quantity}, or would consume more than the
     * available stock. Rendered as HTTP 409 with the
     * {@code INSUFFICIENT_STOCK} code so a client can distinguish it
     * from a duplicate-resource conflict.
     */
    public static BusinessException insufficientStock(String message) {
        return new BusinessException(ErrorCode.INSUFFICIENT_STOCK, message);
    }

    public static BusinessException rateLimited(String message) {
        return new BusinessException(ErrorCode.RATE_LIMITED, message);
    }

    public static BusinessException accountSuspended(String message) {
        return new BusinessException(ErrorCode.ACCOUNT_SUSPENDED, message);
    }

    public static BusinessException vendorNotApproved(String message) {
        return new BusinessException(ErrorCode.VENDOR_NOT_APPROVED, message);
    }

    /**
     * The upload's content is not an accepted image format (plan task 3.8).
     * Rendered as HTTP 415 {@code UNSUPPORTED_MEDIA_TYPE} so a client can tell
     * "wrong format" apart from a malformed request or a broken file.
     */
    public static BusinessException unsupportedMediaType(String message) {
        return new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, message);
    }

    /**
     * The upload exceeds a configured ceiling — the file-size limit, or the
     * decoded pixel budget (plan task 3.8). Rendered as HTTP 413
     * {@code PAYLOAD_TOO_LARGE}.
     */
    public static BusinessException payloadTooLarge(String message) {
        return new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, message);
    }
}
