package com.flowerconnect.vendor.dto;

import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorHoursResponse {

    private DayOfWeek weekday;
    private LocalTime openTime;
    private LocalTime closeTime;
    private boolean closed;
}
