package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.service.DisputeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Admin dispute queue and detail (plan Phase 8, task 8.4).
 *
 * <p>All endpoints require ADMIN role. Resolution is deferred to
 * Phase 8 Stage 5 (task 8.5).
 * Base path: {@code /api/v1/admin/disputes}
 */
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
    @GetMapping
    public ResponseEntity<DisputePageResponse> listDisputes(
            @RequestParam(required = false) DisputeStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listAdminDisputes(status, from, to, pageable));
    }

    /**
     * Admin dispute detail. Returns the full dispute record including
     * private fields (customer email, vendor response, resolution note).
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDetailResponse> getDispute(@PathVariable Long disputeId) {
        return ResponseEntity.ok(disputeService.getAdminDispute(disputeId));
    }

    /**
     * Admin resolution endpoint. Deferred to Phase 8 Stage 5 (task 8.5).
     * Returns 501 Not Implemented in this stage.
     */
    @PostMapping("/{disputeId}/resolve")
    public ResponseEntity<DisputeDetailResponse> resolveDispute(
            @PathVariable Long disputeId,
            @RequestBody Object request) {
        return ResponseEntity.status(501).build();
    }
}