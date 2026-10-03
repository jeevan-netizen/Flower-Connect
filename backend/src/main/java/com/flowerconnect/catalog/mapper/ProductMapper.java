package com.flowerconnect.catalog.mapper;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.ProductImage;
import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.catalog.dto.ProductImageResponse;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.inventory.domain.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * Product read-model mapper (plan task 3.2).
 *
 * <p>The scalar mapping is MapStruct-generated; the inventory
 * summary and the image list are assembled by hand because they
 * come from separate tables (one-to-one and one-to-many) that
 * the service loads alongside the product. {@code available} and
 * {@code lowStock} are computed in {@link #toInventorySummary}
 * rather than stored, so they can never drift from the raw
 * quantities.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductMapper INSTANCE = Mappers.getMapper(ProductMapper.class);

    @Mapping(target = "vendorId", source = "product.vendor.id")
    @Mapping(target = "categoryId", source = "product.category.id")
    @Mapping(target = "categoryName", source = "product.category.name")
    @Mapping(target = "inventory", ignore = true)
    @Mapping(target = "images", ignore = true)
    ProductResponse toResponse(Product product);

    ProductImageResponse toImageResponse(ProductImage image);

    default InventorySummary toInventorySummary(Inventory inventory) {
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

    default ProductResponse toFullResponse(Product product, Inventory inventory, List<ProductImage> images) {
        ProductResponse response = toResponse(product);
        response.setInventory(inventory == null ? null : toInventorySummary(inventory));
        response.setImages(images == null ? List.of() : images.stream().map(this::toImageResponse).toList());
        return response;
    }
}
