package com.flowerconnect.inventory.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Body of {@code PUT /api/v1/vendors/products/{productId}/inventory/expiry-date}
 * (plan task 3.6).
 *
 * <p>Full replacement again, and here {@code null} is a meaningful
 * value rather than an absent one: sending {@code null} clears the
 * expiry date, which is how a vendor says "this product does not
 * expire". An omitted JSON field and an explicit {@code null} both
 * arrive here as {@code null} with Jackson's defaults, so both clear
 * the date — which is the honest reading of a PUT, and is why this
 * endpoint is a PUT and not a PATCH.
 *
 * <p>No stock movement is written. The expiry scheduler (plan task
 * 3.7) is what turns a reached expiry into a {@code WASTE} movement;
 * recording the date is not itself a stock change.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpiryDateRequest {

    /** The new expiry date, or {@code null} to clear it. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;
}