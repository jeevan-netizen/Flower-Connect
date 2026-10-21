package com.flowerconnect.storefront.dto;

import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One weekday of a vendor's operating hours, for the public storefront.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontHour {

    private String weekday;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean closed;
}
