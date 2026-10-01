package com.flowerconnect.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for POST /api/v1/users/me/password.
 * <p>
 * The current password is required to prove ownership. On success the user's
 * other refresh tokens are revoked (the current access token stays valid until
 * its 15-minute expiry but cannot refresh — see docs/known-issues.md).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String newPassword;
}