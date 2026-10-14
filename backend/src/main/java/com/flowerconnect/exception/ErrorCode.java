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
    VENDOR_NOT_APPROVED,
    /**
     * An uploaded file's content is not one of the accepted media types
     * (plan task 3.8 accepts JPEG, PNG and WebP by content sniffing). HTTP 415,
     * because the request itself was well-formed and carried a payload this
     * endpoint does not serve — distinct from {@code VALIDATION_FAILED}, which
     * means the request was malformed.
     */
    UNSUPPORTED_MEDIA_TYPE,
    /**
     * An upload exceeds a configured ceiling: the file-size limit, or the decoded
     * pixel budget that stops a small archive claiming gigabytes of memory.
     * HTTP 413.
     */
    PAYLOAD_TOO_LARGE,
    /**
     * The requested delivery slot has reached its {@code max_orders_per_slot}
     * capacity. HTTP 409, distinct from {@code INSUFFICIENT_STOCK} so a client
     * can tell "the slot is full" apart from "the vendor ran out of stock".
     */
    SLOT_FULL,
    /**
     * A shop's subtotal is below the vendor's {@code min_order_amount}.
     * HTTP 400 because the request is valid but the business rule is not met.
     */
    MINIMUM_ORDER
}
