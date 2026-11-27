package com.flowerconnect.farmer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.Builder
public class PassbookEntryResponse {

    private Long id;
    private Type type;
    private LocalDateTime date;
    private BigDecimal runningBalance;
    private BigDecimal amount;
    private Mode mode;
    private Status status;
    private String note;
    private String flowerType;
    private BigDecimal grossKg;
    private BigDecimal deductionKg;
    private BigDecimal netKg;
    private BigDecimal pricePerKg;

    public enum Type {
        DEPOSIT, COLLECTION
    }

    public enum Mode {
        CASH, UPI
    }

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }
}
