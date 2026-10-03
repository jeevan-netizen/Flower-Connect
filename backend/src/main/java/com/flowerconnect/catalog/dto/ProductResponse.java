package com.flowerconnect.catalog.dto;

import com.flowerconnect.catalog.domain.Product.ProductStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for a product (plan task 3.2).
 *
 * <p>The vendor and category are flattened to their ids (plus the
 * category name for display) so the client can render a product
 * without a second request. The inventory summary and the ordered
 * image list are embedded: both are small, always-read-with-the-
 * product data, and the catalog API returns them together.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private Long vendorId;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private ProductStatus status;
    private InventorySummary inventory;
    private List<ProductImageResponse> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
