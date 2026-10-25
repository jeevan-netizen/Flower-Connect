package com.flowerconnect.notification.controller;

import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.dto.NotificationResponse;
import com.flowerconnect.notification.mapper.NotificationMapper;
import com.flowerconnect.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * In-app notifications for the authenticated user (plan Phase 9, task 9.2).
 *
 * <p>The namespace is open to every authenticated role -- a customer, a
 * florist and an administrator each receive their own notifications -- and
 * the recipient is derived from the JWT subject on every call. No route
 * accepts a user identifier, so cross-user access is not expressible in the
 * request at all: a missing or foreign notification id is a 404 and the
 * caller never learns whether it exists.
 *
 * <p>The controller is thin: it resolves the caller from {@link
 * Authentication#getName()}, enforces ownership through {@link
 * NotificationService}, and maps entities to {@link NotificationResponse}
 * via {@link NotificationMapper}.
 */
@Slf4j
@Validated
@Tag(name = "Notifications", description = "In-app notifications for the authenticated user")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper mapper;

    @Operation(summary = "List the caller's notifications",
            description = "Returns one page of the authenticated user's own "
                    + "notifications, newest first, with their read state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's notification page"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an authenticated account")
    })
    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> listNotifications(
            Authentication authentication,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var notificationsPage = notificationService.listNotifications(authentication.getName(), pageable);
        return ResponseEntity.ok(toPageResponse(notificationsPage));
    }

    @Operation(summary = "Count the caller's unread notifications",
            description = "Returns the number of the authenticated user's "
                    + "notifications whose {@code readAt} is still null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The unread count"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an authenticated account")
    })
    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(Authentication authentication) {
        return ResponseEntity.ok(notificationService.countUnread(authentication.getName()));
    }

    @Operation(summary = "Mark one notification as read",
            description = "Marks one of the authenticated user's notifications "
                    + "as read. A missing or foreign id is a 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked read"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an authenticated account"),
            @ApiResponse(responseCode = "404", description = "No such notification, or it belongs to another user")
    })
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
            Authentication authentication,
            @PathVariable @Positive(message = "Notification id must be positive") Long notificationId) {
        var notification = notificationService.markAsRead(authentication.getName(), notificationId);
        return ResponseEntity.ok(mapper.toResponse(notification));
    }

    @Operation(summary = "Mark all notifications as read",
            description = "Marks every unread notification of the authenticated "
                    + "user as read and returns the number of rows marked.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications marked read"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an authenticated account")
    })
    @PostMapping("/read-all")
    public ResponseEntity<Integer> markAllAsRead(Authentication authentication) {
        int marked = notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(marked);
    }

    private PageResponse<NotificationResponse> toPageResponse(
            org.springframework.data.domain.Page<Notification> page) {
        return PageResponse.<NotificationResponse>builder()
                .content(page.getContent().stream().map(mapper::toResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
