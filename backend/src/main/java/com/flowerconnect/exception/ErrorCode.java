package com.flowerconnect.exception;

public enum ErrorCode {
    VALIDATION_FAILED,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    /**
     * A stock operation would drive {@code quantity} below
     * {@code reserved_quantity}, or would consume more than the
     * available stock (plan section 6.2). HTTP 409, like a plain
     * conflict, but with its own code so a client can tell "the
     * vendor changed the numbers" apart from "this resource already
     * exists".
     */
    INSUFFICIENT_STOCK,
    RATE_LIMITED,
    ACCOUNT_SUSPENDED,
    VENDOR_NOT_APPROVED
}
