package com.flowerconnect.notification.integration;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level coverage for the notification read API (plan Phase 9, task 9.2)
 * against a real MySQL instance and the production security chain.
 *
 * <p>Notifications are persisted directly through the repository, so each
 * test can place rows in any read state and exercise the caller's own list,
 * unread count, mark-one-read and mark-all-read endpoints without depending
 * on the async listener.
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerAToken;
    private Long customerAUserId;
    private String customerBToken;
    private Long customerBUserId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        User customerA = createUser("notify-customer-a-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerAUserId = customerA.getId();
        customerAToken = tokenFor(customerA);

        User customerB = createUser("notify-customer-b-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerBUserId = customerB.getId();
        customerBToken = tokenFor(customerB);

        adminToken = tokenFor(createUser("notify-admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));

        // Customer A: five notifications, newest first; two already read.
        Long nA1 = saveNotification(customerAUserId, "order_placed", "Order placed", "A1",
                LocalDateTime.of(2026, 10, 25, 10, 0), null);
        Long nA2 = saveNotification(customerAUserId, "order_confirmed", "Order confirmed", "A2",
                LocalDateTime.of(2026, 10, 25, 9, 0), null);
        Long nA3 = saveNotification(customerAUserId, "order_delivered", "Delivered", "A3",
                LocalDateTime.of(2026, 10, 25, 8, 0),
                LocalDateTime.of(2026, 10, 25, 8, 30));
        Long nA4 = saveNotification(customerAUserId, "refund_pending", "Refund pending", "A4",
                LocalDateTime.of(2026, 10, 25, 7, 0), null);
        Long nA5 = saveNotification(customerAUserId, "dispute_opened", "Dispute opened", "A5",
                LocalDateTime.of(2026, 10, 25, 6, 0),
                LocalDateTime.of(2026, 10, 25, 6, 30));

        // Customer B: one notification Customer A must never see.
        saveNotification(customerBUserId, "order_placed", "B1", "Customer B notification",
                LocalDateTime.of(2026, 10, 25, 10, 0), null);
    }

    // ------------------------------------------------------------------
    // List
    // ------------------------------------------------------------------

    @Test
    void callerSeesOnlyTheirOwnNotificationsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.content[0].title").value("Order placed"))
                .andExpect(jsonPath("$.content[1].title").value("Order confirmed"))
                .andExpect(jsonPath("$.content[2].title").value("Delivered"))
                .andExpect(jsonPath("$.content[3].title").value("Refund pending"))
                .andExpect(jsonPath("$.content[4].title").value("Dispute opened"))
                // Customer B's notification is absent.
                .andExpect(jsonPath("$.content[*].title", Matchers.not(Matchers.hasItem("B1"))));
    }

    @Test
    void listIsPaginated() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Order placed"))
                .andExpect(jsonPath("$.content[1].title").value("Order confirmed"));
    }

    // ------------------------------------------------------------------
    // Unread count
    // ------------------------------------------------------------------

    @Test
    void unreadCountExcludesReadNotifications() throws Exception {
        // Customer A has 5 notifications, 2 read (A3, A5) -> 3 unread.
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));

        // Customer B has 1 unread notification.
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    // ------------------------------------------------------------------
    // Mark one read
    // ------------------------------------------------------------------

    @Test
    void markAsReadMarksTheCallersOwnNotification() throws Exception {
        // nA2 (title "Order confirmed", created 09:00) is unread.
        Long notificationId = findNotificationId(customerAUserId, "Order confirmed");

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(notificationId.intValue()))
                .andExpect(jsonPath("$.readAt").value(Matchers.notNullValue()));

        // The unread count drops by one.
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    void markAsReadRefusesANotificationBelongingToAnotherUser() throws Exception {
        Long notificationBId = findNotificationId(customerBUserId, "B1");

        mockMvc.perform(patch("/api/v1/notifications/" + notificationBId + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isNotFound());

        // Customer B's notification remains unread.
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(1));
    }

    @Test
    void markAsReadRefusesAMissingNotification() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/999999/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Mark all read
    // ------------------------------------------------------------------

    @Test
    void markAllAsReadMarksEveryUnreadNotification() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0));
    }

    // ------------------------------------------------------------------
    // RBAC
    // ------------------------------------------------------------------

    @Test
    void rbacRefusesAnonymousAndServesEveryAuthenticatedRole() throws Exception {
        // Anonymous is refused.
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        // Customer B sees their own (empty of A's notifications).
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // Admin sees their own (empty) page.
        mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private User createUser(String email, String password, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test " + roleName)
                .phone("+91" + randomDigits(10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
        return user;
    }

    private Long saveNotification(Long userId, String type, String title, String message,
                                  LocalDateTime createdAt, LocalDateTime readAt) {
        Notification n = notificationRepository.saveAndFlush(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .targetType("ORDER")
                .targetId(1L)
                .readAt(readAt)
                .build());
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt), n.getId());
        if (readAt != null) {
            jdbcTemplate.update(
                    "UPDATE notifications SET read_at = ? WHERE id = ?",
                    Timestamp.valueOf(readAt), n.getId());
        }
        return n.getId();
    }

    private Long findNotificationId(Long userId, String title) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId,
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent().stream()
                .filter(n -> title.equals(n.getTitle())).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Notification not found for user " + userId + " title " + title))
                .getId();
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
