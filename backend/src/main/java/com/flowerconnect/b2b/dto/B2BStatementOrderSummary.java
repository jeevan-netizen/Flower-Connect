package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read model for one order summarised within a statement preview or detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BStatementOrderSummary {

    private Long id;
    private String orderNo;
    private Status status;
    private PaymentStatus paymentStatus;
    private BigDecimal total;
    private LocalDateTime deliveredAt;
    private Long buyerId;
    private Long sellerId;
}
