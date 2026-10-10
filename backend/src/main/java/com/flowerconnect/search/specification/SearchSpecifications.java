package com.flowerconnect.search.specification;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.inventory.domain.Inventory;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Reusable query fragments for the public product search (plan task 4.4).
 *
 * <p>All predicates express the <em>eligibility</em> of a product for the
 * storefront: {@code ACTIVE} status, positive available stock, and a vendor
 * that is {@code APPROVED}, accepting orders, and within delivery radius.
 * The optional filters ({@code q}, {@code category}, {@code priceMin},
 * {@code priceMax}, {@code vendorId}) are composed on top of this base.
 */
public final class SearchSpecifications {

    private SearchSpecifications() {
    }

    /**
     * Base eligibility: product is ACTIVE, has available stock (> 0), and
     * its vendor is APPROVED and accepting orders.
     *
     * <p>The vendor radius check requires the Haversine distance, which
     * cannot be expressed in JPQL/SQL portably, so the radius is refined in
     * Java (see {@code SearchService}) against the vendor ids this
     * specification is handed.
     *
     * <p>Positive available stock is expressed as a correlated {@code EXISTS}
     * subquery over {@code inventory}, because {@code Inventory} owns the
     * foreign key and {@code Product} deliberately has no inverse mapping
     * (adding one would make every product query join the inventory table).
     */
    public static Specification<Product> eligibleForStorefront(
            List<Long> vendorIdsInRadius,
            List<Long> categoryIds) {

        return (root, query, builder) -> {
            query.distinct(true);

            // Product status = ACTIVE
            var activePredicate = builder.equal(root.get("status"), ProductStatus.ACTIVE);

            // EXISTS (SELECT 1 FROM inventory i
            //         WHERE i.product = product
            //           AND i.quantity - i.reservedQuantity > 0)
            var stockSubquery = query.subquery(Integer.class);
            var inventory = stockSubquery.from(Inventory.class);
            stockSubquery.select(builder.literal(1))
                    .where(builder.equal(inventory.get("product"), root),
                            builder.greaterThan(
                                    builder.diff(inventory.get("quantity"),
                                            inventory.get("reservedQuantity")),
                                    0));
            var stockPredicate = builder.exists(stockSubquery);

            // Vendor must be APPROVED and acceptingOrders = true
            var vendorJoin = root.join("vendor", JoinType.INNER);
            var vendorStatusPredicate = builder.equal(vendorJoin.get("status"),
                    com.flowerconnect.domain.VendorProfile.Status.APPROVED);
            var vendorAcceptingPredicate = builder.isTrue(vendorJoin.get("acceptingOrders"));

            // Optional: restrict to vendor IDs that are within delivery radius
            // (computed in Java via Haversine). If the list is empty, no
            // vendor can match, so we return a contradiction.
            var vendorIdPredicate = vendorIdsInRadius.isEmpty()
                    ? builder.disjunction()
                    : vendorJoin.get("id").in(vendorIdsInRadius);

            // Optional: restrict to category IDs (including descendants)
            var categoryPredicate = (categoryIds == null || categoryIds.isEmpty())
                    ? builder.conjunction()
                    : root.get("category").get("id").in(categoryIds);

            return builder.and(
                    activePredicate,
                    stockPredicate,
                    vendorStatusPredicate,
                    vendorAcceptingPredicate,
                    vendorIdPredicate,
                    categoryPredicate
            );
        };
    }

    /**
     * Case-insensitive, partial match on product name. A blank or null
     * term yields a predicate that matches everything.
     */
    public static Specification<Product> nameContains(String term) {
        String needle = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> needle.isEmpty()
                ? builder.conjunction()
                : builder.like(builder.lower(root.get("name")), "%" + needle + "%");
    }

    /**
     * Price lower bound on {@code base_price}.
     */
    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, builder) -> min == null
                ? builder.conjunction()
                : builder.greaterThanOrEqualTo(root.get("basePrice"), min);
    }

    /**
     * Price upper bound on {@code base_price}.
     */
    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, builder) -> max == null
                ? builder.conjunction()
                : builder.lessThanOrEqualTo(root.get("basePrice"), max);
    }

    /**
     * Restricts results to a single vendor (by id). Still subject to the
     * approval/reach filters from {@link #eligibleForStorefront}.
     */
    public static Specification<Product> forVendor(Long vendorId) {
        return (root, query, builder) -> vendorId == null
                ? builder.conjunction()
                : builder.equal(root.get("vendor").get("id"), vendorId);
    }
}