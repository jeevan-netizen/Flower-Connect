package com.flowerconnect.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.mapper.UserMapper;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ChangePasswordRequest;
import com.flowerconnect.security.dto.ProfileUpdateRequest;
import com.flowerconnect.security.dto.UserResponse;
import com.flowerconnect.security.jwt.RefreshTokenService;
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
