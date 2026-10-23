package com.flowerconnect.customer.order.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * One item snapshot within a customer order (plan Phase 6, task 6.3).
 *
 * <p>The price and name are captured at checkout time and never updated,
 * so the order is an immutable record of what the customer agreed to pay,
 * independent of later price changes on the product.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderItemResponse {

    private String productName;
    private int quantity;
    private BigDecimal priceSnapshot;
    private BigDecimal subtotal;
    private String note;
}
