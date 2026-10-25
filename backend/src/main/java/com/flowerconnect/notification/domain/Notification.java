package com.flowerconnect.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * In-app notification for a user (plan Phase 9, task 9.2).
 *
 * <p>One row per notification, owned by {@code user_id}. {@code read_at} is
 * nullable; null means unread. {@code dedupe_key} is nullable with a unique
 * index so redelivery of the same event creates at most one row per recipient.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", length = 64, nullable = false)
    private String type;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "message", length = 1000, nullable = false)
    private String message;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;
}
