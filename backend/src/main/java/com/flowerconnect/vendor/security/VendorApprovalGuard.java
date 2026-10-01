package com.flowerconnect.vendor.security;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * The single owner of the "is this vendor approved?" rule (plan task 2.7).
 *
 * <p>Approval is read from the database on every guarded request and is never
 * cached in the JWT or in a filter-local field. That is deliberate: an admin
 * approval must take effect on the vendor's very next request, and a suspension
 * must hide the vendor immediately (D-6), without waiting for access-token expiry.
 *
 * <p>Ownership is resolved from the {@link Authentication} the JWT filter
 * installed, never from a request parameter, so a vendor cannot address another
 * vendor's profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorApprovalGuard {

    /**
     * Application role for a vendor. The plan text calls this role "VENDOR"; the
     * seeded role is FLORIST and is not renamed in this phase. See
     * docs/decisions.md (D-11).
     */
    public static final String VENDOR_ROLE = "FLORIST";

    public static final String AUTHORITY_VENDOR = "ROLE_" + VENDOR_ROLE;

    public static final String MESSAGE_NO_VENDOR_ROLE =
            "This endpoint is available to approved vendors only";
    public static final String MESSAGE_NO_PROFILE =
            "No vendor profile exists for this account";
    public static final String MESSAGE_PENDING =
            "Vendor account is awaiting administrator approval";
    public static final String MESSAGE_REJECTED =
            "Vendor application was rejected";
    public static final String MESSAGE_SUSPENDED =
            "Vendor account is suspended";

    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Authorization predicate used by {@code @RequiresApprovedVendor}.
     *
     * <p>It deliberately raises {@link VendorNotApprovedException} instead of
     * returning {@code false}: returning {@code false} would produce Spring
     * Security's opaque "Access Denied" body, whereas the thrown exception carries
     * the approval-specific message and error code. The method therefore returns
     * {@code true} on success only.
     *
     * @throws VendorNotApprovedException if the caller is not a vendor, has no
     *                                     profile, or the profile is not
     *                                     {@code APPROVED}
     */
    public boolean isApproved(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("Approval gate refused an unauthenticated caller");
            throw new VendorNotApprovedException(MESSAGE_NO_VENDOR_ROLE);
        }
        if (!isVendor(authentication)) {
            log.debug("Approval gate refused {}: not a vendor account", authentication.getName());
            throw new VendorNotApprovedException(MESSAGE_NO_VENDOR_ROLE);
        }

        VendorProfile profile = vendorProfileRepository.findByUserEmail(authentication.getName())
                .orElseThrow(() -> {
                    log.debug("Approval gate refused {}: no vendor profile", authentication.getName());
                    return new VendorNotApprovedException(MESSAGE_NO_PROFILE);
                });

        if (profile.getStatus() != VendorProfile.Status.APPROVED) {
            log.debug("Approval gate refused {}: profile status is {}",
                    authentication.getName(), profile.getStatus());
            throw new VendorNotApprovedException(messageFor(profile.getStatus()));
        }
        return true;
    }

    /**
     * True when the caller holds the vendor role. Uses the authorities granted by
     * the JWT filter rather than trusting a client-supplied claim shape.
     */
    public boolean isVendor(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> AUTHORITY_VENDOR.equals(authority.getAuthority()));
    }

    private static String messageFor(VendorProfile.Status status) {
        return switch (status) {
            case PENDING_APPROVAL -> MESSAGE_PENDING;
            case REJECTED -> MESSAGE_REJECTED;
            case SUSPENDED -> MESSAGE_SUSPENDED;
            case APPROVED -> throw new IllegalStateException(
                    "APPROVED is not a refusal; the caller must not reach messageFor for it");
        };
    }
}
