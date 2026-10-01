package com.flowerconnect.vendor.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Raised when a vendor reaches an {@code @RequiresApprovedVendor} endpoint without
 * an {@code APPROVED} vendor profile (plan task 2.7).
 *
 * <p>It extends Spring Security's {@link AccessDeniedException} because it is
 * produced while a method-security authorization rule is being evaluated, so the
 * status it maps to is 403 rather than 500. {@code GlobalExceptionHandler} renders
 * it with the dedicated {@code VENDOR_NOT_APPROVED} error code so a client can
 * distinguish "not approved yet" from a plain permission failure.
 */
public class VendorNotApprovedException extends AccessDeniedException {

    public VendorNotApprovedException(String message) {
        super(message);
    }
}
