package com.flowerconnect.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NotificationService}: persistence, dedupe, and
 * nonexistent-user handling.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService service;

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
        // simulate the unique-constraint violation on save
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
}
