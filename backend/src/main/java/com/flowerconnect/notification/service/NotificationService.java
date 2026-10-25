package com.flowerconnect.notification.service;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

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
}
