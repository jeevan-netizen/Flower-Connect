package com.flowerconnect.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.flowerconnect.domain.User;
import lombok.*;

/**
 * Status change requested by an administrator (plan task 2.8).
 *
 * <p>The reason is mandatory for every transition, including reinstatement. It is
 * stored on the {@code audit_log} row written by the action, which is the only
 * place the platform records why an account was suspended or disabled.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private User.Status status;

    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}