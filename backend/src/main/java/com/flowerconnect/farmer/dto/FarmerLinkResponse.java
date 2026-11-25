package com.flowerconnect.farmer.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerLinkResponse {

    private Long id;
    private Long vendorId;
    private String vendorName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
