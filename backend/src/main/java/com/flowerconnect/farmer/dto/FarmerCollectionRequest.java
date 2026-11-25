package com.flowerconnect.farmer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class FarmerCollectionRequest {

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Mode is required")
    private Mode mode;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;

    public enum Mode {
        CASH, UPI
    }
}
