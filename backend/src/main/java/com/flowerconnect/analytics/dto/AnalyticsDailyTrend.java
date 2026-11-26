package com.flowerconnect.analytics.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily trend data point. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDailyTrend {

    /** Calendar day (ISO-8601). Zero-filled days are included with zero values. */
    private LocalDate date;

    private Long orderCount;
    private BigDecimal revenue;
}
