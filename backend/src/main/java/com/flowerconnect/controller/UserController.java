package com.flowerconnect.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.mapper.UserMapper;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ChangePasswordRequest;
import com.flowerconnect.security.dto.ProfileUpdateRequest;
import com.flowerconnect.security.dto.UserResponse;
import com.flowerconnect.security.jwt.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@Validated
@Tag(name = "Current user", description = "Self-service profile and password for the authenticated customer")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserController(UserRepository userRepository, UserMapper userMapper,
                          PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @Operation(summary = "Get the current user's profile",
            description = "Returns the authenticated user's profile. Requires a valid session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User profile"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    /**
     * Update the current user's full name and phone. Email is not editable
     * through this endpoint (it is the account identity). Unknown JSON fields
     * are ignored (see {@link ProfileUpdateRequest}).
     */
    @PatchMapping("/me")
    @Transactional
    @Operation(summary = "Update the current user's profile",
            description = "Updates the authenticated user's full name and phone. Email is not editable "
                    + "through this endpoint. Requires a valid session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "409", description = "Phone already in use")
    })
    public ResponseEntity<UserResponse> updateProfile(
            Authentication authentication,
            @Valid @org.springframework.web.bind.annotation.RequestBody ProfileUpdateRequest request) {
        String email = authentication.getName();
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            if (userRepository.existsByPhone(request.getPhone())
                    && !request.getPhone().equals(user.getPhone())) {
                throw BusinessException.conflict("Phone number already in use");
            }
            user.setPhone(request.getPhone());
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(userMapper.toResponse(saved));
    }

    /**
     * Change the current user's password. Requires the current password to be
     * correct. On success, all of the user's other refresh tokens are revoked
     * so sessions on other devices end; the current access token stays valid
     * until its 15-minute expiry but cannot refresh (see docs/known-issues.md).
     */
    @PostMapping("/me/password")
    @Transactional
    @Operation(summary = "Change the current user's password",
            description = "Changes the authenticated user's password after verifying the current one. "
                    + "Revokes all other refresh tokens on success. Requires a valid session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "400", description = "Current password is incorrect or validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangePasswordRequest request) {
        String email = authentication.getName();
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        refreshTokenService.revokeAllRefreshTokensForUser(user.getId());

        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }
}
