package com.flowerconnect.analytics.dto;

import lombok.*;

import java.math.BigDecimal;

/** One top-vendor entry in the analytics overview. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsTopVendor {

    private Long vendorId;
    private String businessName;
    private BigDecimal marketplaceSales;
    private Long deliveredOrders;
}
