package com.flowerconnect.security.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Request body for PATCH /api/v1/users/me.
 * <p>
 * Email is intentionally not part of this DTO: it is the account identity and
 * is not editable through the profile endpoint. Unknown JSON fields are
 * ignored (PATCH semantics) rather than rejected, so a typo in the request
 * body is silently dropped instead of producing a 400.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileUpdateRequest {

    @Size(max = 128, message = "Full name must not exceed 128 characters")
    private String fullName;

    @jakarta.validation.constraints.Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be a valid phone number")
    private String phone;
}