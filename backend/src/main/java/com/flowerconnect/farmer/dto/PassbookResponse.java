package com.flowerconnect.farmer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PassbookResponse {

    private Long linkId;
    private Long farmerId;
    private String farmerName;
    private Long vendorId;
    private String vendorName;
    private String linkStatus;
    private BigDecimal outstandingBalance;
    private List<PassbookEntryResponse> entries;
}
