package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.domain.DisputeStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Admin resolution of a dispute (plan Phase 8, task 8.5 - implemented in Stage 5).
 * Not used in Stage 4 (task 8.4) but defined here for the queue response.
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
    private DisputeStatus status;

    /**
     * Admin's resolution note. Required, max 500 chars.
     */
    @NotBlank
    @Size(max = 500)
    private String resolutionNote;
}