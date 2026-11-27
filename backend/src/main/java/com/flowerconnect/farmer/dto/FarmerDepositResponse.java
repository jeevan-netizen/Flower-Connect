package com.flowerconnect.farmer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class FarmerDepositResponse {

    private Long id;
    private Long linkId;
    private String flowerType;
    private BigDecimal grossKg;
    private BigDecimal deductionKg;
    private BigDecimal netKg;
    private BigDecimal pricePerKg;
    private BigDecimal amount;
    private LocalDateTime depositedAt;
    private LocalDateTime createdAt;
}
