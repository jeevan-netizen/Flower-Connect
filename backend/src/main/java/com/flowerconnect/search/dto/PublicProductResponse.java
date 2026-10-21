package com.flowerconnect.search.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public product read model for search, storefront and product-detail endpoints.
 *
 * <p>Intentionally excludes {@code status}, internal timestamps, and image
 * {@code storageKey} (filesystem/storage internals) so no internal data leaks to
 * unauthenticated customers.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicProductResponse {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;
    private Integer available;
    private List<PublicProductImageResponse> images;
}
