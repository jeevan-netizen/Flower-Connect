package com.flowerconnect.farmer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerVendorResponse {

    private Long id;
    private String businessName;
    private String vendorStatus;
    private String linkStatus;
}
