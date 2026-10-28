package com.flowerconnect.review.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Public review response for storefront display (plan Phase 12, task 12.2).
 *
 * <p>Customer identity is masked (first name + last initial only). Customer
 * and user IDs, and the order ID, are deliberately never exposed to the public.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponse {

    private Long id;
    private Integer rating;
    private String comment;
    private String customerName;
    private String vendorReply;
    private LocalDateTime vendorRepliedAt;
    private LocalDateTime createdAt;
}
