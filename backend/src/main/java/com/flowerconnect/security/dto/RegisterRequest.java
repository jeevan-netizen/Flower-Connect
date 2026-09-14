package com.flowerconnect.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 128, message = "Full name must not exceed 128 characters")
    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be a valid phone number")
    private String phone;
}
