package com.flowerconnect.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.LocalDate;

/**
 * Request to create a B2B statement. The caller is resolved as the seller;
 * the request selects the buyer and an optional date range.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateB2BStatementRequest {

    @NotNull(message = "Buyer id is required")
    @Positive(message = "Buyer id must be positive")
    private Long buyerId;

    private LocalDate from;

    private LocalDate to;
}
