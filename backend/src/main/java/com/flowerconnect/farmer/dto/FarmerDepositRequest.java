package com.flowerconnect.farmer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class FarmerDepositRequest {

    @NotBlank(message = "Flower type is required")
    @Size(max = 100, message = "Flower type must not exceed 100 characters")
    private String flowerType;

    @NotNull(message = "Gross kg is required")
    @Positive(message = "Gross kg must be positive")
    @Digits(integer = 8, fraction = 2, message = "Gross kg must have at most 2 decimal places")
    private BigDecimal grossKg;

    @NotNull(message = "Price per kg is required")
    @Positive(message = "Price per kg must be positive")
    @Digits(integer = 8, fraction = 2, message = "Price per kg must have at most 2 decimal places")
    private BigDecimal pricePerKg;

    @PastOrPresent(message = "Deposited at must not be in the future")
    private LocalDateTime depositedAt;
}
