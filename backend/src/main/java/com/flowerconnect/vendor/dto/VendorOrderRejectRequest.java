package com.flowerconnect.vendor.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderRejectRequest {

    @NotBlank(message = "Reason is required to reject an order")
    private String reason;
}
