package com.flowerconnect.farmer.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerLinkResponse {

    private Long id;
    private Long vendorId;
    private Long farmerId;
    private String vendorName;
    private String farmerName;
    private String status;
    private BigDecimal outstandingBalance;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
