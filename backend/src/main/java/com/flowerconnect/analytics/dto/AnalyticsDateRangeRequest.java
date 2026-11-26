package com.flowerconnect.analytics.dto;

import lombok.*;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Request parameters for date-range analytics queries. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDateRangeRequest {

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;
}
