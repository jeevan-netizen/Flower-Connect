package com.flowerconnect.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for adding a product to the authenticated customer's cart
 * (plan task 5.2).
 *
 * <p>Cart items are never assigned a price from the client: the price snapshot
 * is taken from {@code products.base_price} at add time (decision C-1) and is
 * the only price that contributes to the cart total until checkout copies it to
 * {@code order_items}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddCartItemRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Size(max = 1000, message = "Note must be at most 1000 characters")
    private String note;
}
