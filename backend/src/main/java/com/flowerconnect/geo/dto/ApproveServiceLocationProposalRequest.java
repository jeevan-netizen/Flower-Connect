package com.flowerconnect.geo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

/**
 * Supplies the location data an administrator must provide to approve a
 * proposal: coordinates and the IANA timezone. The registration form cannot
 * provide these and must not invent them.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApproveServiceLocationProposalRequest {

    @NotNull(message = "Latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    private BigDecimal longitude;

    @NotBlank(message = "Timezone is required")
    @Size(max = 64, message = "Timezone must not exceed 64 characters")
    private String timezone;
}
