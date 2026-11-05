package com.flowerconnect.vendor.controller;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import com.flowerconnect.vendor.dto.VendorProfilePageResponse;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.service.VendorAdminService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Admin vendor management (plan task 2.6). Reachable only with
 * {@code ROLE_ADMIN} (see {@code SecurityConfig}); every action writes an
 * {@code audit_log} row.
 *
 * <p>Each action is a separate route rather than a generic status update so that
 * a route can only ever perform one legal transition, and so the recorded audit
 * action is unambiguous.
 */
@Slf4j
@Validated
@Tag(name = "Admin vendor management", description = "ADMIN-only: approve, reject, suspend, reinstate and list vendors")
@RestController
@RequestMapping("/api/v1/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final VendorAdminService vendorAdminService;

    /**
     * Lists vendors, optionally filtered by approval status.
     */
    @GetMapping
    @Operation(summary = "List vendors (admin)",
            description = "Returns one page of vendor profiles, optionally filtered by "
                    + "approval status. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<VendorProfilePageResponse> listVendors(
            @RequestParam(required = false) VendorProfile.Status status,
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(100) Integer size) {

        int safePage = page != null ? page : 0;
        int safeSize = size != null ? size : 20;

        return ResponseEntity.ok(vendorAdminService.listProfiles(status, safePage, safeSize));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending vendor",
            description = "Approves a PENDING_APPROVAL vendor. Writes an audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor approved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    public ResponseEntity<VendorProfileResponse> approve(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id) {
        return ResponseEntity.ok(vendorAdminService.approve(authentication.getName(), id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending vendor",
            description = "Rejects a PENDING_APPROVAL vendor; a non-blank reason is required. "
                    + "Writes an audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor rejected"),
            @ApiResponse(responseCode = "400", description = "Reason missing"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    public ResponseEntity<VendorProfileResponse> reject(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id,
            @Valid @RequestBody VendorAdminReasonRequest request) {
        return ResponseEntity.ok(
                vendorAdminService.reject(authentication.getName(), id, request.getReason()));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a vendor",
            description = "Suspends an APPROVED vendor; a non-blank reason is required. "
                    + "In-flight orders continue (D-6). Writes an audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor suspended"),
            @ApiResponse(responseCode = "400", description = "Reason missing"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    public ResponseEntity<VendorProfileResponse> suspend(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id,
            @Valid @RequestBody VendorAdminReasonRequest request) {
        return ResponseEntity.ok(
                vendorAdminService.suspend(authentication.getName(), id, request.getReason()));
    }

    @PostMapping("/{id}/reinstate")
    @Operation(summary = "Reinstate a suspended vendor",
            description = "Reinstates a SUSPENDED vendor to APPROVED. Writes an audit-log row. "
                    + "ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor reinstated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    public ResponseEntity<VendorProfileResponse> reinstate(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id) {
        return ResponseEntity.ok(vendorAdminService.reinstate(authentication.getName(), id));
    }
}
