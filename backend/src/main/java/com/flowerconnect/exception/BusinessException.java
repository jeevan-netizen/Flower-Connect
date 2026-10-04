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
}
