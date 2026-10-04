package com.flowerconnect.inventory.mapper;

import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.domain.StockMovement;
import com.flowerconnect.inventory.dto.LowStockProductResponse;
import com.flowerconnect.inventory.dto.StockMovementResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * Inventory read-model mappers (plan task 3.6).
 *
 * <p>{@link #toSummary} and {@link #toLowStockEntry} are the same
 * computation written once per shape: {@code available} is
 * {@code quantity − reservedQuantity} and {@code lowStock} is
 * {@code available <= lowStockThreshold}. Both are computed, never
 * read back from a column, so a stored level can never drift from what
 * the API reports.
 *
 * <p>{@link #toMovementResponse} flattens the two associations a
 * history page needs — the product and the actor — into ids. MapStruct
 * emits the null guards for those, which is what makes a
 * system-initiated movement (no actor) map cleanly instead of throwing.
 */
@Mapper(componentModel = "spring")
public interface StockMovementMapper {

    StockMovementMapper INSTANCE = Mappers.getMapper(StockMovementMapper.class);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "actorUserId", source = "actor.id")
    @Mapping(target = "actorEmail", source = "actor.email")
    StockMovementResponse toMovementResponse(StockMovement movement);

    /** Same rule as {@code ProductMapper.toInventorySummary}, for a standalone read. */
    default InventorySummary toSummary(Inventory inventory) {
        return InventorySummary.builder()
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .available(inventory.getAvailable())
                .lowStockThreshold(inventory.getLowStockThreshold())
                .expiryDate(inventory.getExpiryDate())
                .lowStock(inventory.getAvailable() <= inventory.getLowStockThreshold())
                .build();
    }

    /** The same numbers plus the product name, which only the list endpoint knows to show. */
    default LowStockProductResponse toLowStockEntry(Inventory inventory) {
        return LowStockProductResponse.builder()
                .productId(inventory.getProduct().getId())
                .productName(inventory.getProduct().getName())
                .quantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .available(inventory.getAvailable())
                .lowStockThreshold(inventory.getLowStockThreshold())
                .expiryDate(inventory.getExpiryDate())
                .lowStock(true)
                .build();
    }
}