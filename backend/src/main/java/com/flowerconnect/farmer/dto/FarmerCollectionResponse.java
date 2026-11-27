package com.flowerconnect.farmer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class FarmerCollectionResponse {

    private Long id;
    private Long linkId;
    private BigDecimal amount;
    private Mode mode;
    private String note;
    private Status status;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;

    public enum Mode {
        CASH, UPI
    }

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }
}
