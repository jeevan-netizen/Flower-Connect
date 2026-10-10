package com.flowerconnect.catalog.specification;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Reusable query fragments for the vendor-scoped product listing
 * (plan task 3.5).
 *
 * <p>Every predicate here is optional and is composed only when the
 * caller supplies the corresponding filter, so the vendor scope is
 * the one mandatory fragment — {@link #forVendor(Long)} is passed
 * first and every optional fragment is appended to it with
 * {@code and}. A blank search string is treated as no filter at all
 * rather than as a predicate matching nothing, because
 * {@code name=} on a query string is a client artefact and not an
 * intent to find products with an empty name.
 *
 * <p>Name matching is case-insensitive and partial. The name column is
 * not indexed, so this is a scan within the vendor's own rows; the
 * vendor predicate narrows that first.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    /** Restricts a query to one vendor. This is the mandatory fragment of the vendor listing. */
    public static Specification<Product> forVendor(Long vendorId) {
        return (root, query, builder) -> builder.equal(root.get("vendor").get("id"), vendorId);
    }

    /** Matches products in exactly this lifecycle status. */
    public static Specification<Product> withStatus(ProductStatus status) {
        return (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    /** Matches products assigned to one category. */
    public static Specification<Product> inCategory(Long categoryId) {
        return (root, query, builder) -> builder.equal(root.get("category").get("id"), categoryId);
    }

    /**
     * Matches products whose name contains {@code term}, ignoring case. A null or
     * blank term yields a predicate that matches everything, so callers may pass
     * the raw request value through unchanged.
     */
    public static Specification<Product> nameContains(String term) {
        String needle = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> needle.isEmpty()
                ? builder.conjunction()
                : builder.like(builder.lower(root.get("name")), "%" + needle + "%");
    }
}