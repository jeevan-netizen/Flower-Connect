package com.flowerconnect.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request body for updating a cart item's quantity (plan task 5.2).
 *
 * <p>A quantity of zero is the signal to remove the line: the service deletes
 * the cart item rather than storing a zero (the database CHECK on
 * {@code cart_items.quantity} rejects zero anyway — D-12: service and database
 * both enforce the positive-quantity invariant).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity must be zero or positive")
    private Integer quantity;
}
