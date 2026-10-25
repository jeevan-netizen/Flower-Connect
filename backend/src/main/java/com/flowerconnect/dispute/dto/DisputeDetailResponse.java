package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.domain.DisputeStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full dispute detail for customer/vendor/admin views (plan Phase 8, task 8.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeDetailResponse {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private DisputeStatus status;
    private String reasonCode;
    private String description;
    private String customerName;
    private String customerEmail;
    private String vendorName;
    private BigDecimal orderTotal;
    private BigDecimal refundAmount;
    private String vendorResponse;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
}