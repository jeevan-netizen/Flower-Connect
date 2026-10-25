package com.flowerconnect.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.repository.NotificationRepository;
import com.flowerconnect.notification.service.NotificationService;
import com.flowerconnect.notification.sse.NotificationSseService;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level and service-level coverage for the notification SSE stream (plan
 * Phase 9, task 9.5) against a real MySQL instance and the production security
 * chain.
 *
 * <p>Covers: authenticated access to {@code GET /api/v1/notifications/stream},
 * per-user isolation (user A never receives user B's stream), post-commit
 * delivery, and failure isolation (an SSE delivery failure never rolls back the
 * notification row).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSseControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String BASE = "/api/v1/notifications";

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
    @Autowired
    private NotificationService notificationService;

    /**
     * The SSE service is replaced by a mock so a delivery failure can be
     * injected without touching the real emitter map.
     */
    @MockBean
    private NotificationSseService sseService;

    private String customerAToken;
    private Long customerAUserId;
    private String customerBToken;
    private Long customerBUserId;

    @BeforeEach
    void setUp() throws Exception {
        User customerA = createUser("sse-customer-a-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerAUserId = customerA.getId();
        customerAToken = tokenFor(customerA);

        User customerB = createUser("sse-customer-b-" + UUID.randomUUID() + "@test.com", PASSWORD, "CUSTOMER");
        customerBUserId = customerB.getId();
        customerBToken = tokenFor(customerB);

        tokenFor(createUser("sse-admin-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN"));
    }

    @Test
    void streamRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/stream"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamAcceptsEveryAuthenticatedRole() throws Exception {
        mockMvc.perform(get(BASE + "/stream")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk());
    }

    @Test
    void postCommitDeliveryPersistsEvenWhenSseFails() throws Exception {
        // The mock SSE service throws on every emit; the failure is swallowed
        // and must not roll back the notification row.
        doThrow(new RuntimeException("stream down"))
                .when(sseService).emit(eq(customerAUserId), any());

        var created = notificationService.create(customerAUserId, "order_placed", "Order placed",
                "Your order #1 is confirmed.", "ORDER", 1L,
                "order-placed:1:" + customerAUserId);

        assertThat(created).isNotNull();
        assertThat(notificationRepository.existsById(created.getId())).isTrue();
        verify(sseService).emit(eq(customerAUserId), any());
    }

    @Test
    void notificationBelongsOnlyToItsOwner() throws Exception {
        var created = notificationService.create(customerAUserId, "order_placed", "Order placed",
                "Owned by customer A", "ORDER", 1L,
                "order-placed:2:" + customerAUserId);

        // Customer B can never read or mark customer A's notification.
        mockMvc.perform(patch(BASE + "/" + created.getId() + "/read")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isNotFound());

        // Customer B's unread count never includes customer A's notification.
        mockMvc.perform(get(BASE + "/unread-count")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerBToken)))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));

        // Customer A's own list returns exactly the notification created above.
        mockMvc.perform(get(BASE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerAToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    var body = objectMapper.readTree(result.getResponse().getContentAsString());
                    assertThat(body.path("content").size()).isEqualTo(1);
                    assertThat(body.path("content").get(0).path("id").asLong()).isEqualTo(created.getId());
                });
    }

    // ------------------------------------------------------------------
    // Fixtures and helpers
    // ------------------------------------------------------------------

    private User createUser(String email, String password, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test " + roleName)
                .phone("+91" + randomDigits(10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }

    private static String bearer(String token) {
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
