package com.flowerconnect.b2b.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Summary of an approved vendor returned by the B2B seller search.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SellerSummary {

    private Long vendorId;
    private String businessName;
    private String addressLine1;
    private String city;
    private String area;
    private String pincode;
    private BigDecimal deliveryRadiusKm;
}
