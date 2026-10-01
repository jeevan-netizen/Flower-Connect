package com.flowerconnect.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request body for POST /api/v1/auth/forgot-password.
 * <p>
 * The response is identical whether or not the email exists (rules.md 5.6):
 * no user enumeration. The service layer therefore never inspects the result
 * to decide the response shape.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForgotPasswordRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
}