package com.flowerconnect.vendor.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a handler (or a whole controller) as vendor-only in the stricter sense of
 * plan task 2.7: only a caller whose {@code vendor_profiles.status} is
 * {@code APPROVED} may reach it.
 *
 * <p>This is deliberately distinct from the {@code hasRole("FLORIST")} rule in
 * {@code SecurityConfig}. The role check answers "is this a vendor account?"; this
 * annotation answers "may this vendor transact?" A {@code PENDING_APPROVAL},
 * {@code REJECTED} or {@code SUSPENDED} vendor still owns the vendor role and can
 * therefore still reach {@code GET|PUT /api/v1/vendors/profile}, but not any
 * annotated endpoint.
 *
 * <p>Apply it to every catalog, inventory and order endpoint added from Phase 3
 * onward. See docs/decisions.md (D-13).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@PreAuthorize("@vendorApprovalGuard.isApproved(authentication)")
public @interface RequiresApprovedVendor {
}
