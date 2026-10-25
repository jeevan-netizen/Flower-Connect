package com.flowerconnect.notification.repository;

import com.flowerconnect.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for {@link Notification} (plan Phase 9, task 9.2).
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Page of a user's notifications, newest first.
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    /**
     * Page of a user's unread notifications, newest first.
     */
    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            @Param("userId") Long userId, Pageable pageable);

    /**
     * Count of a user's unread notifications.
     */
    long countByUserIdAndReadAtIsNull(@Param("userId") Long userId);

    /**
     * Whether a notification with the given dedupe key already exists.
     * Used for idempotent redelivery.
     */
    boolean existsByDedupeKey(@Param("dedupeKey") String dedupeKey);

    /**
     * All notifications for a user with a given type, newest first.
     */
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            @Param("userId") Long userId, @Param("type") String type);
}
