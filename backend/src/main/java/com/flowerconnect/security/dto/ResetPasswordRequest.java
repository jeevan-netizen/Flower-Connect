package com.flowerconnect.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for POST /api/v1/auth/reset-password.
 * <p>
 * The token is a single-use, hashed, 30-minute-lived value issued by
 * {@code /auth/forgot-password}. The endpoint returns one generic error
 * (400) for not-found / expired / already-used tokens so an attacker cannot
 * distinguish which state a token is in (rules.md 5.6).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String newPassword;
}