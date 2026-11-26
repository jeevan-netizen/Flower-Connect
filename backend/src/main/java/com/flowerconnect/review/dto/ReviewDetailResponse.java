package com.flowerconnect.review.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full review detail for authenticated owners (customer, vendor, admin).
 *
 * <p>Unlike {@link ReviewResponse}, this may expose the order ID and customer
 * ID because the caller is authenticated and is the owner of the review or an
 * administrator.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDetailResponse {

    private Long id;
    private Long orderId;
    private Long customerId;
    private Integer rating;
    private String comment;
    private String customerName;
    private Boolean hidden;
    private String hiddenReason;
    private String vendorReply;
    private LocalDateTime vendorRepliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
