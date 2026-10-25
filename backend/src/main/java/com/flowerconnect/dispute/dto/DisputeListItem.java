package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.domain.DisputeStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dispute list item for customer/vendor/admin views (plan Phase 8, task 8.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputeListItem {

    private Long id;
    private Long orderId;
    private String orderNumber; // "Order #123" for display
    private DisputeStatus status;
    private String reasonCode;
    private String customerName;
    private String vendorName;
    private BigDecimal orderTotal;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}