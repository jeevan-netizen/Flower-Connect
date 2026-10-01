package com.flowerconnect.vendor.specification;

import com.flowerconnect.domain.VendorProfile;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable query fragments for vendor discovery (plan task 2.7).
 *
 * <p>Discovery must only ever return {@code APPROVED} profiles, so the predicate
 * is defined once here rather than re-declared per endpoint. Composing with
 * {@code Specification#and} keeps the approval constraint mandatory at the point
 * of use: a discovery query that forgets it is visible as a missing call rather
 * than as a silently wrong result set.
 *
 * <p>{@code vendor_profiles} carries {@code idx_vendor_profiles_status_geo}, so
 * {@link #approved()} can be satisfied from the leading status column of that
 * index.
 */
public final class VendorProfileSpecifications {

    private VendorProfileSpecifications() {
    }

    /**
     * Matches only vendors an administrator has approved. Every discovery query
     * must compose this predicate.
     */
    public static Specification<VendorProfile> approved() {
        return (root, query, builder) ->
                builder.equal(root.get("status"), VendorProfile.Status.APPROVED);
    }
}
