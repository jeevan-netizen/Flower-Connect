package com.flowerconnect.search.specification;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Reusable Criteria predicates for public product eligibility (plan tasks 4.3, 4.4, 4.5).
 *
 * <p>Every predicate is optional and composed only when the caller supplies the
 * corresponding filter. Stock availability ({@code quantity - reservedQuantity > 0})
 * is applied in the service layer after a batch inventory fetch, because Product does
 * not map a back-reference to Inventory.
 */
public final class PublicProductSpecifications {

    private PublicProductSpecifications() {
    }

    /** Restricts results to one or more qualifying vendors. */
    public static Specification<Product> forVendorIds(List<Long> vendorIds) {
        return (root, query, builder) -> root.get("vendor").get("id").in(vendorIds);
    }

    /** Matches products in {@code ACTIVE} status — the only storefront-visible status. */
    public static Specification<Product> activeProduct() {
        return (root, query, builder) -> builder.equal(root.get("status"), ProductStatus.ACTIVE);
    }

    /** Matches products assigned to exactly this category. */
    public static Specification<Product> inCategory(Long categoryId) {
        return (root, query, builder) -> builder.equal(root.get("category").get("id"), categoryId);
    }

    /**
     * Matches products whose name contains {@code term}, ignoring case. A null or blank
     * term yields a predicate that matches everything.
     */
    public static Specification<Product> nameContains(String term) {
        String needle = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> needle.isEmpty()
                ? builder.conjunction()
                : builder.like(builder.lower(root.get("name")), "%" + needle + "%");
    }

    /** Matches products whose base price falls within {@code [min, max]}, bounds optional. */
    public static Specification<Product> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, builder) -> {
            if (min == null && max == null) {
                return builder.conjunction();
            }
            if (min == null) {
                return builder.lessThanOrEqualTo(root.get("basePrice"), max);
            }
            if (max == null) {
                return builder.greaterThanOrEqualTo(root.get("basePrice"), min);
            }
            return builder.between(root.get("basePrice"), min, max);
        };
    }

    /** Restricts results to a single vendor (still subject to eligibility filters). */
    public static Specification<Product> vendorId(Long vendorId) {
        return (root, query, builder) -> builder.equal(root.get("vendor").get("id"), vendorId);
    }
}
