package com.flowerconnect.notification.repository;

import com.flowerconnect.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persistence for {@link Notification} (plan Phase 9, task 9.2).
 *
 * <p>Read-state mutations scope every update by {@code userId}, so a caller
 * can only mark their own notifications as read. A notification that is
 * missing or belongs to another user matches no row and is reported as not
 * found -- the caller never learns whether a foreign id exists.
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

    // ------------------------------------------------------------------
    // Read-state transitions (plan Phase 9, task 9.2)
    // ------------------------------------------------------------------

    /**
     * One notification by id, owned by the given user. Empty when the row is
     * missing or belongs to another user, so ownership is enforced by the
     * query itself.
     */
    Optional<Notification> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Marks the given notification as read for its owner. Returns the number
     * of rows updated (0 when the notification is missing or foreign).
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.id = :id AND n.userId = :userId AND n.readAt IS NULL")
    int markReadByIdAndUserId(
            @Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    /**
     * Marks all of a user's unread notifications as read. Returns the number
     * of rows updated.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllReadByUserId(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
