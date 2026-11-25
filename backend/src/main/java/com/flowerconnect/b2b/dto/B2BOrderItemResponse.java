package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line item in a B2B order response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BOrderItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String unit;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
}
