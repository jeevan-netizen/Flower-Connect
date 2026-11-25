package com.flowerconnect.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * Request to place a new B2B order.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceB2BOrderRequest {

    @NotNull(message = "Seller id is required")
    @Positive(message = "Seller id must be positive")
    private Long sellerId;

    @NotNull(message = "At least one item is required")
    private List<B2BOrderItemRequest> items;

    @Size(max = 1000, message = "Order note must not exceed 1000 characters")
    private String orderNote;
}
