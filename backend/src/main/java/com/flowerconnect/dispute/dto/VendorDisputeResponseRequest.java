package com.flowerconnect.dispute.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Vendor's response to a dispute on their order (plan Phase 8, task 8.4).
 * Moves the dispute from {@code OPEN} to {@code UNDER_REVIEW}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorDisputeResponseRequest {

    /**
     * Vendor's response/defense. Required, max 500 chars.
     */
    @NotBlank
    @Size(max = 500)
    private String response;
}