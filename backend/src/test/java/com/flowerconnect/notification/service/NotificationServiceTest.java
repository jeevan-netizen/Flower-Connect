package com.flowerconnect.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for {@link NotificationService}: persistence, dedupe, and
 * nonexistent-user handling (task 9.1), plus the read-state transitions of
 * task 9.2.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant FIXED = Instant.parse("2026-10-25T10:00:00Z");
    private static final LocalDateTime READ_AT = LocalDateTime.of(2026, 10, 25, 10, 0, 0);

    @Mock
    private NotificationRepository repository;

    @Mock
    private UserRepository userRepository;

    private Clock clock;
    private NotificationService service;

    @BeforeEach
    void init() {
        clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        service = new NotificationService(repository, userRepository, clock);
    }

    @Test
    void createSavesNotificationForExistingUser() {
        when(userRepository.existsById(7L)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.create(7L, "order_placed", "Order placed",
                "Your order #1 is confirmed.", "ORDER", 1L, "order-placed:1:7");

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(7L);
        assertThat(result.getType()).isEqualTo("order_placed");
        verify(repository).save(any(Notification.class));
    }

    @Test
    void createSkipsNonexistentUser() {
        when(userRepository.existsById(999L)).thenReturn(false);

        Notification result = service.create(999L, "order_placed", "Order placed",
                "Your order #1 is confirmed.", "ORDER", 1L, "order-placed:1:999");

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void createIsIdempotentWithDuplicateDedupeKey() {
        when(userRepository.existsById(7L)).thenReturn(true);
        when(repository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        Notification result = service.create(7L, "order_placed", "Order placed",
                "Your order #1 is confirmed.", "ORDER", 1L, "order-placed:1:7");

        assertThat(result).isNull();
        verify(repository).save(any(Notification.class));
    }

    @Test
    void createWithoutDedupeKeySavesEveryTime() {
        when(userRepository.existsById(7L)).thenReturn(true);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification r1 = service.create(7L, "order_placed", "Order placed", "msg",
                "ORDER", 1L, null);
        Notification r2 = service.create(7L, "order_placed", "Order placed", "msg",
                "ORDER", 1L, null);

        assertThat(r1).isNotNull();
        assertThat(r2).isNotNull();
        verify(repository, times(2)).save(any(Notification.class));
    }

    // ------------------------------------------------------------------
    // Read API (task 9.2)
    // ------------------------------------------------------------------

    @Test
    void listNotificationsReturnsTheUsersOwnPageNewestFirst() {
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        Notification older = notification(1L, 7L, "order_placed", "Older", null);
        older.setCreatedAt(LocalDateTime.of(2026, 10, 24, 9, 0, 0));
        Notification newer = notification(2L, 7L, "order_confirmed", "Newer", null);
        newer.setCreatedAt(LocalDateTime.of(2026, 10, 25, 9, 0, 0));
        when(repository.findByUserIdOrderByCreatedAtDesc(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(0, 10), 2));

        var page = service.listNotifications("a@test.com",
                PageRequest.of(0, 10, Sort.by("createdAt").descending()));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getId()).isEqualTo(2L);
        assertThat(page.getContent().get(1).getId()).isEqualTo(1L);
        verify(repository).findByUserIdOrderByCreatedAtDesc(eq(7L), any());
    }

    @Test
    void listNotificationsResolvesUserFromEmailOnly() {
        when(userRepository.findByEmailWithRole("b@test.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listNotifications("b@test.com", PageRequest.of(0, 10)))
                .isInstanceOf(IllegalStateException.class);
        verify(repository, never()).findByUserIdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void countUnreadCountsOnlyUnreadNotifications() {
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        when(repository.countByUserIdAndReadAtIsNull(7L)).thenReturn(3L);

        long count = service.countUnread("a@test.com");

        assertThat(count).isEqualTo(3L);
        verify(repository).countByUserIdAndReadAtIsNull(7L);
    }

    @Test
    void markAsReadSetsReadAtAndEnforcesOwnership() {
        Notification notification = notification(5L, 7L, "order_placed", "Order placed", null);
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(notification));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Notification result = service.markAsRead("a@test.com", 5L);

        assertThat(result.getReadAt()).isEqualTo(READ_AT);
        verify(repository).save(notification);
    }

    @Test
    void markAsReadRefusesANotificationBelongingToAnotherUser() {
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead("a@test.com", 5L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        verify(repository, never()).save(any());
    }

    @Test
    void markAsReadRefusesAMissingNotification() {
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        when(repository.findByIdAndUserId(999L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead("a@test.com", 999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
        verify(repository, never()).save(any());
    }

    @Test
    void markAllAsReadMarksEveryUnreadNotificationAndReturnsTheCount() {
        when(userRepository.findByEmailWithRole("a@test.com"))
                .thenReturn(Optional.of(newUser(7L, "a@test.com")));
        when(repository.markAllReadByUserId(eq(7L), any(LocalDateTime.class))).thenReturn(4);

        int marked = service.markAllAsRead("a@test.com");

        assertThat(marked).isEqualTo(4);
        verify(repository).markAllReadByUserId(7L, READ_AT);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static User newUser(Long id, String email) {
        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("hash")
                .fullName("Test User")
                .build();
    }

    private static Notification notification(Long id, Long userId, String type, String title, String message) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .build();
    }
}
