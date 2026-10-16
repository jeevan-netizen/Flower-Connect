
package com.flowerconnect.vendor.dto;

import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotResponse {

    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private DayOfWeek weekday;
    private boolean available;
}

