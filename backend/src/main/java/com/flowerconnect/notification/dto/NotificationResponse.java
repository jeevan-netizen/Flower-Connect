package com.flowerconnect.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * The read model for one in-app notification (plan Phase 9, task 9.2).
 *
 * <p>Only fields safe to expose to the recipient are included: the
 * notification category, its title and message, an optional deep-link
 * target, and the read/create timestamps. Internal event and entity details
 * are deliberately omitted.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String message;
    private String targetType;
    private Long targetId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
