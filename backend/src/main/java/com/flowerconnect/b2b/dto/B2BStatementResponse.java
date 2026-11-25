package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BStatement;
import com.flowerconnect.b2b.domain.B2BStatement.PaymentMode;
import com.flowerconnect.b2b.domain.B2BStatement.Status;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Read model for a B2B statement, returned for create, list and detail.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BStatementResponse {

    private Long id;
    private String statementNo;
    private Long sellerId;
    private String sellerName;
    private Long buyerId;
    private String buyerName;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private BigDecimal totalAmount;
    private Status status;
    private LocalDateTime paidAt;
    private PaymentMode paymentMode;
    private String paymentNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<B2BStatementOrderSummary> orders;
}
