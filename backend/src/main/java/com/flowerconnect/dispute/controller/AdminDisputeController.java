package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.ResolveDisputeRequest;
import com.flowerconnect.dispute.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Admin dispute queue, detail, and resolution (plan Phase 8, task 8.4/8.5).
 *
 * <p>All endpoints require ADMIN role. Resolution is implemented in
 * Phase 8 Stage 5 (task 8.5).
 * Base path: {@code /api/v1/admin/disputes}
 */
@Slf4j
@Validated
@Tag(name = "Admin dispute queue", description = "Admin-only dispute queue, detail, and resolution")
@RestController
@RequestMapping("/api/v1/admin/disputes")
@RequiredArgsConstructor
public class AdminDisputeController {

    private final DisputeService disputeService;

    /**
     * Paginated dispute queue with optional status and date filters.
     *
     * @param status  optional status filter (OPEN, UNDER_REVIEW, RESOLVED_*)
     * @param from    optional start of creation date range (inclusive, UTC)
     * @param to      optional end of creation date range (exclusive, UTC)
     * @param pageable pagination
     * @return paginated dispute list
     */
    @Operation(summary = "List disputes",
            description = "Returns one page of disputes, ordered newest first. Optional "
                    + "status, from and to filters are supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute queue page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    @GetMapping
    public ResponseEntity<DisputePageResponse> listDisputes(
            @Parameter(description = "Status filter (OPEN, UNDER_REVIEW, RESOLVED_*)")
            @RequestParam(required = false) DisputeStatus status,
            @Parameter(description = "Lower bound on created_at (inclusive, ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Upper bound on created_at (exclusive, ISO-8601)")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listAdminDisputes(status, from, to, pageable));
    }

    /**
     * Admin dispute detail. Returns the full dispute record including
     * private fields (customer email, vendor response, resolution note, refund amount).
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDetailResponse> getDispute(@PathVariable Long disputeId) {
        return ResponseEntity.ok(disputeService.getAdminDispute(disputeId));
    }

    /**
     * Admin resolution endpoint. Resolves a dispute to one of
     * {@code RESOLVED_REFUND}, {@code RESOLVED_PARTIAL}, or {@code RESOLVED_REJECTED}.
     *
     * @param authentication the authenticated admin
     * @param disputeId      the dispute to resolve
     * @param request        resolution status, note, and optional refund amount
     * @return the updated dispute detail
     * @throws BusinessException 404 if dispute not found, 400 for invalid request,
     *                           409 if dispute not resolvable or amount invalid
     */
    @Operation(summary = "Resolve a dispute",
            description = "Resolves an OPEN or UNDER_REVIEW dispute to "
                    + "RESOLVED_REFUND, RESOLVED_PARTIAL, or RESOLVED_REJECTED. "
                    + "Requires a resolution note. For refund outcomes, refundAmount is "
                    + "required and validated against the order total. For REJECTED, "
                    + "refundAmount must not be provided.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute resolved"),
            @ApiResponse(responseCode = "400", description = "Invalid request (status, note, or amount)"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Dispute not found"),
            @ApiResponse(responseCode = "409", description = "Dispute not in resolvable state or amount invalid")
    })
    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeDetailResponse> resolveDispute(
            Authentication authentication,
            @PathVariable Long disputeId,
            @Valid @RequestBody ResolveDisputeRequest request) {
        DisputeDetailResponse response = disputeService.resolveDispute(
                authentication.getName(), disputeId, request);
        log.info("Admin {} resolved dispute {} as {}",
                authentication.getName(), disputeId, request.getStatus());
        return ResponseEntity.ok(response);
    }
}