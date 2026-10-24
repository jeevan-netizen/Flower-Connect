package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.domain.DisputeStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Admin resolution of a dispute (plan Phase 8, task 8.5).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveDisputeRequest {

    /**
     * Final status after resolution. Must be one of the RESOLVED_* values.
     */
    @NotNull(message = "Resolution status is required")
    private DisputeStatus status;

    /**
     * Admin's resolution note. Required, max 500 chars.
     */
    @NotBlank(message = "Resolution note is required")
    @Size(max = 500, message = "Resolution note must be 500 characters or fewer")
    private String resolutionNote;

    /**
     * Refund amount for RESOLVED_REFUND and RESOLVED_PARTIAL outcomes.
     * Required and must be > 0 for those statuses; must be null for RESOLVED_REJECTED.
     * For RESOLVED_REFUND, the service will verify it equals the order total.
     * For RESOLVED_PARTIAL, must be > 0 and <= order total.
     * Rounded to 2 decimal places with HALF_UP (INR).
     */
    @DecimalMin(value = "0.01", inclusive = true, message = "Refund amount must be greater than zero")
    private BigDecimal refundAmount;
}