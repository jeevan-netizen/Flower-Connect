package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for a B2B order, returned for both list and detail endpoints.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BOrderResponse {

    private Long id;
    private String orderNo;
    private Long buyerId;
    private String buyerName;
    private Long sellerId;
    private String sellerName;
    private Status status;
    private PaymentStatus paymentStatus;
    private BigDecimal total;
    private String orderNote;
    private String shipmentReference;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private String billNumber;
    private LocalDateTime placedAt;
    private LocalDateTime updatedAt;
    private List<B2BOrderItemResponse> items;
}
