package com.flowerconnect.inventory.specification;

import com.flowerconnect.inventory.domain.Inventory;
import jakarta.persistence.criteria.Path;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable query fragments for the vendor-scoped inventory listings
 * (plan task 3.6).
 *
 * <p>The low-stock list is defined by the plan as a "low-stock list",
 * and the condition is the one the {@code InventorySummary.lowStock}
 * flag already uses on the product read model:
 * {@code available <= lowStockThreshold}, where
 * {@code available = quantity − reservedQuantity}.
 *
 * <p>That predicate cannot be a derived query method: it compares an
 * expression over three columns against one column, and Spring Data
 * has no method-name syntax for the subtraction of two mapped
 * fields. It is written with the Criteria API instead, following the
 * {@link com.flowerconnect.catalog.specification.ProductSpecifications}
 * precedent from task 3.5 and for the same reason (D-23): a JPQL
 * string would make Hibernate infer parameter types from a null side,
 * which is no better on a numeric column than on a native-ENUM one.
 *
 * <p>{@code lowStockThreshold} is per product, so the comparison is a
 * column against a column rather than against a request parameter:
 * there is nothing for a caller to supply and therefore nothing that
 * could be inferred or injected wrongly.
 */
public final class InventorySpecifications {

    private InventorySpecifications() {
    }

    /**
     * Restricts a query to one vendor. This is the mandatory fragment of every
     * vendor-facing inventory listing — the same ownership rule the catalog API
     * applies, expressed over the inventory table.
     */
    public static Specification<Inventory> forVendor(Long vendorId) {
        return (root, query, builder) -> builder.equal(
                root.get("product").get("vendor").get("id"), vendorId);
    }

    /**
     * Matches inventory rows whose availability has reached or fallen below the
     * vendor's own threshold.
     */
    public static Specification<Inventory> atOrBelowThreshold() {
        return (root, query, builder) -> {
            // Typed locals rather than chained root.get(...) calls: the
            // bare call has to infer its generic from the comparison
            // below, and inference then resolves to Object and no
            // lessThanOrEqualTo overload applies.
            Path<Integer> quantity = root.get("quantity");
            Path<Integer> reserved = root.get("reservedQuantity");
            Path<Integer> threshold = root.get("lowStockThreshold");
            return builder.lessThanOrEqualTo(builder.diff(quantity, reserved), threshold);
        };
    }
}