package com.flowerconnect.vendor.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * One day of a vendor's operating week as supplied by a client.
 *
 * <p>Only the weekday and the {@code closed} flag are shape-validated here. The
 * cross-field rules (a closed day carries no times; an open day carries both
 * times and closes after it opens) depend on more than one property, so they are
 * enforced once, in {@code VendorService}, rather than being duplicated here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorHoursRequest {

    @NotNull(message = "Weekday is required")
    private DayOfWeek weekday;

    private LocalTime openTime;

    private LocalTime closeTime;

    @NotNull(message = "Closed flag is required")
    private Boolean closed;
}
