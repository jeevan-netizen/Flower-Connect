package com.flowerconnect.geo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceLocationProposalRequest {

    @NotBlank(message = "City is required")
    @Size(max = 128, message = "City must not exceed 128 characters")
    private String city;

    @NotBlank(message = "Area is required")
    @Size(max = 128, message = "Area must not exceed 128 characters")
    private String area;

    @NotBlank(message = "Pincode is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be a 6-digit number")
    @Size(max = 10, message = "Pincode must not exceed 10 characters")
    private String pincode;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    private String addressLine2;
}
