package com.flowerconnect.farmer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerLinkRequest {

    @NotNull(message = "Vendor id is required")
    @Positive(message = "Vendor id must be a positive identifier")
    private Long vendorId;
}
