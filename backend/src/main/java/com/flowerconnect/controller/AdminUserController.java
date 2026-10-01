package com.flowerconnect.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.security.AdminUserService;
import com.flowerconnect.security.UserStatusService;
import com.flowerconnect.security.dto.AdminUserPageResponse;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.dto.UserStatusUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Admin user status management (plan task 2.8). Reachable only with
 * {@code ROLE_ADMIN} (see {@code SecurityConfig}).
 *
 * <p>Both routes delegate to services: the listing to {@link AdminUserService},
 * the status change to {@link UserStatusService}, which is the single owner of
 * the rule and also performs the refresh-token revocation and the audit write.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserStatusService userStatusService;

    /**
     * Lists users, optionally filtered by role and status.
     */
    @GetMapping
    public ResponseEntity<AdminUserPageResponse> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) User.Status status,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(
                adminUserService.listUsers(role, status, safePage, safeSize));
    }

    /**
     * Changes a user's account status. The reason is mandatory and is stored on
     * the {@code audit_log} row written for the change.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminUserResponse> updateStatus(
            Authentication authentication,
            @PathVariable @Positive(message = "User id must be positive") Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {

        return ResponseEntity.ok(userStatusService.changeStatus(
                authentication.getName(), id, request.getStatus(), request.getReason()));
    }
}