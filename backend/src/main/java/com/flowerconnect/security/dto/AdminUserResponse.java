package com.flowerconnect.security.dto;

import com.flowerconnect.domain.User;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A user as seen by an administrator (plan task 2.8).
 *
 * <p>Distinct from {@link UserResponse}, which is the caller's own profile and
 * carries no status. This representation adds {@code status} because listing and
 * status management are exactly the operations an admin performs.
 *
 * <p>It deliberately carries no credential material: no password hash, no token
 * hash, no refresh-token identifier.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private User.Status status;
    private LocalDateTime createdAt;
}