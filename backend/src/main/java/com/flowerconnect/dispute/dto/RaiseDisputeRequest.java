package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.domain.DisputeStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request to raise a dispute for a delivered order (plan Phase 8, task 8.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RaiseDisputeRequest {

    /**
     * Fixed reason code from the allowed vocabulary. Not free text so the
     * admin queue can be filtered/grouped by reason.
     */
    private String reasonCode;

    /**
     * Customer's free-text description of the problem. Max 2000 chars.
     */
    private String description;
}