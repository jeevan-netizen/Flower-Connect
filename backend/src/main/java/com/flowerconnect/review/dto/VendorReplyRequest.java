package com.flowerconnect.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Vendor reply to a review (plan Phase 12, task 12.2).
 *
 * <p>One reply per review; a second attempt returns 409. The reply is trimmed
 * and capped at 1000 characters. A suspended vendor may still reply to reviews
 * on their delivered orders (D-6).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorReplyRequest {

    /**
     * The vendor's public reply to the review. Required, max 1000 chars.
     */
    @NotBlank(message = "Vendor reply is required")
    @Size(max = 1000, message = "Vendor reply must be 1000 characters or fewer")
    private String reply;
}
