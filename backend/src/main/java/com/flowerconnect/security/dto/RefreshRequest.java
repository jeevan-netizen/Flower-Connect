package com.flowerconnect.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
