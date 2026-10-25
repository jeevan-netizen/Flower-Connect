package com.flowerconnect.notification.service;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.domain.User;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Persistence for in-app notifications (plan Phase 9, task 9.2).
 *
 * <p>All writes go through a single {@code REQUIRES_NEW} method so a
 * notification row commits independently of the originating business
 * transaction. A rolled-back business transaction therefore never creates a
 * notification row.
 *
 * <p>Recipients are resolved from scalar IDs only; the listener layer loads
 * any entity data it needs before calling here. Deleted or nonexistent users
 * are silently skipped (no notification, no exception).
 *
 * <p>The read API (task 9.2) resolves the recipient from the authenticated
 * principal (the JWT subject) and enforces ownership in every query, so a
 * caller can never read or mark another user's notifications.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * Creates one notification row in its own transaction.
     *
     * @param userId      the recipient (must exist)
     * @param type        notification category
     * @param title       short title
     * @param message     body text
     * @param targetType  nullable deep-link target type (ORDER, DISPUTE, ...)
     * @param targetId    nullable deep-link target id
     * @param dedupeKey   nullable idempotency key; when present and already
     *                    used, the insert is skipped idempotently
     * @return the created row, or null if the user does not exist or the
     *          dedupe key already exists
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification create(Long userId, String type, String title, String message,
                               String targetType, Long targetId, String dedupeKey) {
        if (!userRepository.existsById(userId)) {
            log.debug("Skipping notification for nonexistent user id={} type={}", userId, type);
            return null;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .targetType(targetType)
                .targetId(targetId)
                .dedupeKey(dedupeKey)
                .build();

        try {
            return repository.save(notification);
        } catch (DataIntegrityViolationException ex) {
            // Duplicate dedupe key from redelivery: idempotent, no-op.
            log.debug("Dedupe key already exists, skipping duplicate notification: {}", dedupeKey);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Read API (plan Phase 9, task 9.2)
    // ------------------------------------------------------------------

    /**
     * The authenticated user, resolved from the JWT subject only.
     */
    private User requireUser(String email) {
        return userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    /**
     * One page of the given user's notifications, newest first.
     */
    @Transactional(readOnly = true)
    public Page<Notification> listNotifications(String email, Pageable pageable) {
        User user = requireUser(email);
        return repository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
    }

    /**
     * Count of the given user's unread notifications.
     */
    @Transactional(readOnly = true)
    public long countUnread(String email) {
        User user = requireUser(email);
        return repository.countByUserIdAndReadAtIsNull(user.getId());
    }

    /**
     * Marks one of the given user's notifications as read. Ownership is
     * enforced by {@link NotificationRepository#findByIdAndUserId}: a missing
     * or foreign id is a 404 and the caller never learns whether it exists.
     */
    @Transactional
    public Notification markAsRead(String email, Long notificationId) {
        User user = requireUser(email);
        Notification notification = repository.findByIdAndUserId(notificationId, user.getId())
                .orElseThrow(() -> BusinessException.notFound("Notification not found: " + notificationId));
        notification.setReadAt(LocalDateTime.now(clock));
        return repository.save(notification);
    }

    /**
     * Marks all of the given user's unread notifications as read. Returns the
     * number of rows marked.
     */
    @Transactional
    public int markAllAsRead(String email) {
        User user = requireUser(email);
        return repository.markAllReadByUserId(user.getId(), LocalDateTime.now(clock));
    }
}
