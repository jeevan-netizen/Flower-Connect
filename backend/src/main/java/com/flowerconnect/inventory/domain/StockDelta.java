package com.flowerconnect.inventory.domain;

/**
 * One product's stock delta used by order-state stock side effects
 * (commit reserved stock, release reservation, restock).
 *
 * <p>Kept as a plain value holder so the order module can pass item
 * quantities to {@link com.flowerconnect.inventory.service.InventoryService}
 * without importing order entities into the inventory module.
 */
public record StockDelta(Long productId, int quantity) {
    public StockDelta {
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
