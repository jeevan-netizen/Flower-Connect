package com.flowerconnect.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.farmer.dto.FarmerAdminDetail;
import com.flowerconnect.farmer.dto.FarmerAdminSummary;
import com.flowerconnect.farmer.service.AdminFarmerService;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.dto.UserStatusUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin farmer management (Admin Farmer Management). Reachable only with
 * {@code ROLE_ADMIN} (see {@code com.flowerconnect.config.SecurityConfig}).
 *
 * <p>The list and detail routes are read-only and scope each response to the
 * requested farmer. The status change delegates to
 * {@link com.flowerconnect.security.UserStatusService}, which is the single
 * owner of the account-status rule: it revokes the target's refresh tokens and
 * writes one {@code audit_log} row (actor, timestamp, action, reason) in the
 * same transaction.
 */
@Slf4j
@Validated
@Tag(name = "Admin farmer management", description = "ADMIN-only: farmer list, farmer detail, farmer account status changes")
@RestController
@RequestMapping("/api/v1/admin/farmers")
@RequiredArgsConstructor
public class AdminFarmerController {

    private final AdminFarmerService adminFarmerService;

    /**
     * Lists farmer accounts, optionally narrowed to one account status and/or a
     * free-text search over name, email and mobile number.
     */
    @GetMapping
    @Operation(summary = "List farmers (admin)",
            description = "Returns one page of farmer accounts with optional status and "
                    + "search filters. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Farmer page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<Page<FarmerAdminSummary>> listFarmers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) User.Status status,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(adminFarmerService.listFarmers(search, status, safePage, safeSize));
    }

    /**
     * Returns the detail view for one farmer: the account profile, the linked
     * florists, and a read-only ledger overview separated by vendor.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get a farmer's detail (admin)",
            description = "Returns one farmer's profile, linked florists and a "
                    + "vendor-separated read-only ledger overview. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Farmer detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Farmer not found")
    })
    public ResponseEntity<FarmerAdminDetail> getFarmerDetail(
            @Parameter(description = "The farmer's user id")
            @PathVariable @Positive(message = "Farmer id must be positive") Long id) {

        return ResponseEntity.ok(adminFarmerService.getFarmerDetail(id));
    }

    /**
     * Suspends or reactivates a farmer account. A non-blank reason is mandatory
     * and is stored on the {@code audit_log} row written for the change.
     */
    @PostMapping("/{id}/status")
    @Operation(summary = "Change a farmer's account status (admin)",
            description = "Suspends or reactivates a farmer account. A non-blank "
                    + "reason is mandatory and is stored on the audit_log row. "
                    + "ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Farmer not found"),
            @ApiResponse(responseCode = "409", description = "Self-targeting not allowed")
    })
    public ResponseEntity<AdminUserResponse> changeFarmerStatus(
            Authentication authentication,
            @Parameter(description = "The farmer's user id")
            @PathVariable @Positive(message = "Farmer id must be positive") Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {

        return ResponseEntity.ok(adminFarmerService.changeFarmerStatus(
                authentication.getName(), id, request.getStatus(), request.getReason()));
    }
}
