package com.flowerconnect.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Request to submit a review for a delivered order (plan Phase 12, task 12.2).
 *
 * <p>Rating is bounded 1-5; comment is optional and length-limited. Business
 * rules (order must be DELIVERED, caller must be the order's customer, at most
 * one review per order) are enforced by {@link com.flowerconnect.review.service.ReviewService}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewRequest {

    /**
     * Star rating, 1 to 5 inclusive.
     */
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    /**
     * Optional free-text comment. Max 1000 characters.
     */
    @Size(max = 1000, message = "Comment must be 1000 characters or fewer")
    private String comment;
}
