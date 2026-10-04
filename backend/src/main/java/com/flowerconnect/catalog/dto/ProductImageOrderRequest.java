package com.flowerconnect.catalog.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.util.List;

/**
 * Reorder request for a product's images (plan task 3.8).
 *
 * <p>The list must be a permutation of the product's own image ids — every image
 * exactly once. That is stricter than "here is the new order", and deliberately:
 * a partial list cannot say where the omitted images belong, so accepting one
 * would either drop them or guess. See {@code ProductImageService.reorder}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageOrderRequest {

    @NotEmpty(message = "At least one image id is required")
    private List<@NotNull(message = "Image id must not be null") @Positive(message = "Image id must be positive") Long> imageIds;
}