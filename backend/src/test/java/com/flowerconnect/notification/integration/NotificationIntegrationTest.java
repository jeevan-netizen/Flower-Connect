package com.flowerconnect.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Integration tests for the notifications table (V25 migration), entity and
 * repository against real MySQL via Testcontainers.
 */
@SpringBootTest
class NotificationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long userId;

    @BeforeEach
    void insertUser() {
        Optional<Role> customerRole = roleRepository.findByName("CUSTOMER");
        User user = User.builder()
                .email("notify-user-" + System.nanoTime() + "@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .fullName("Notify Test User")
                .role(customerRole.orElseThrow())
                .status(User.Status.ACTIVE)
                .build();
        userId = userRepository.save(user).getId();
    }

    @Test
    void v25MigrationCreatesNotificationsTable() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() " +
                        "AND table_name = 'notifications'", Integer.class);
        assertThat(count).isEqualTo(1);

        Integer dedupeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                        "AND table_name = 'notifications' AND index_name = 'uq_notifications_dedupe'",
                Integer.class);
        assertThat(dedupeCount).isGreaterThanOrEqualTo(1);

        Integer createdIdx = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                        "AND table_name = 'notifications' AND index_name = 'idx_notifications_user_created'",
                Integer.class);
        assertThat(createdIdx).isGreaterThanOrEqualTo(1);

        Integer unreadIdx = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() " +
                        "AND table_name = 'notifications' AND index_name = 'idx_notifications_user_unread'",
                Integer.class);
        assertThat(unreadIdx).isGreaterThanOrEqualTo(1);
    }

    @Test
    void saveAndFindByUserIdNewestFirst() {
        Notification n1 = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("Your order #1 is confirmed.")
                .targetType("ORDER").targetId(1L)
                .dedupeKey("order-placed:1:" + userId).build();
        Notification n2 = Notification.builder()
                .userId(userId).type("order_confirmed").title("Order confirmed")
                .message("Your order #1 has been confirmed.")
                .targetType("ORDER").targetId(1L)
                .dedupeKey("order-status:1:CONFIRMED:" + userId).build();
        repository.save(n1);
        repository.save(n2);

        List<Notification> page = repository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(page).hasSize(2);
        assertThat(page.get(0).getId()).isEqualTo(n2.getId());
        assertThat(page.get(1).getId()).isEqualTo(n1.getId());
    }

    @Test
    void countUnreadNotifications() {
        Notification unread = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("msg").build();
        Notification read = Notification.builder()
                .userId(userId).type("order_delivered").title("Delivered")
                .message("msg").readAt(LocalDateTime.now()).build();
        repository.save(unread);
        repository.save(read);

        long count = repository.countByUserIdAndReadAtIsNull(userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void dedupeKeyPreventsDuplicateRows() {
        Notification n1 = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("msg").dedupeKey("order-placed:1:" + userId).build();
        repository.save(n1);

        Notification n2 = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("msg").dedupeKey("order-placed:1:" + userId).build();
        boolean threw = false;
        try {
            repository.save(n2);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            threw = true;
        }
        assertThat(threw).isTrue();

        long count = repository.countByUserIdAndReadAtIsNull(userId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nullDedupeKeyAllowsMultipleRows() {
        Notification n1 = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("msg").dedupeKey(null).build();
        Notification n2 = Notification.builder()
                .userId(userId).type("order_placed").title("Order placed")
                .message("msg").dedupeKey(null).build();
        repository.save(n1);
        repository.save(n2);

        List<Notification> page = repository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(page).hasSize(2);
    }
}
