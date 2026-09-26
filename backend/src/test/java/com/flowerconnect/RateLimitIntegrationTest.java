package com.flowerconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ForgotPasswordRequest;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.security.dto.ResetPasswordRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.test.MutableClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for rate limiting on auth endpoints.
 * Rate limiting is enabled via app.rate-limit.enabled=true in this test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "rate-limit-test"})
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Autowired
    private MutableClock mutableClock;

    private String userEmail;
    private String userPhone;
    private Long userId;
    private String password = "password123";

    @BeforeEach
    void setUp() {
        userEmail = "ratelimit-test-" + UUID.randomUUID() + "@test.com";
        userPhone = "+1" + String.format("%010d", new java.util.Random().nextInt(1000000000));

        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email(userEmail)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Rate Limit Test User")
                .phone(userPhone)
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        User saved = userRepository.save(user);
        userId = saved.getId();
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("Exceeding login rate limit returns 429 with Retry-After header")
    void exceedingLoginRateLimitReturns429WithRetryAfter() throws Exception {
        // Make 5 successful login attempts (limit is 5 per hour)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    LoginRequest.builder().email(userEmail).password(password).build())))
                    .andExpect(status().isOk());
        }

        // 6th attempt should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password(password).build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("Exceeding forgot-password rate limit returns 429 with Retry-After header")
    void exceedingForgotPasswordRateLimitReturns429WithRetryAfter() throws Exception {
        // Make 5 successful forgot-password requests (limit is 5 per hour)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    ForgotPasswordRequest.builder().email(userEmail).build())))
                    .andExpect(status().isOk());
        }

        // 6th attempt should be rate limited
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(userEmail).build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("Exceeding reset-password rate limit returns 429 with Retry-After header")
    void exceedingResetPasswordRateLimitReturns429WithRetryAfter() throws Exception {
        // First create a valid reset token by calling forgot-password
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(userEmail).build())))
                .andExpect(status().isOk());

        // Make 5 reset-password attempts (limit is 5 per hour)
        // Include email in request body for rate limiting (controller ignores unknown fields)
        for (int i = 0; i < 5; i++) {
            String body = String.format("{\"token\":\"invalid-token-%d\",\"newPassword\":\"newPassword123\",\"email\":\"%s\"}", i, userEmail);
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest()); // Invalid token returns 400, but counts toward rate limit
        }

        // 6th attempt should be rate limited
        String body = String.format("{\"token\":\"invalid-token-5\",\"newPassword\":\"newPassword123\",\"email\":\"%s\"}", userEmail);
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("Different emails have independent rate limits")
    void differentEmailsHaveIndependentRateLimits() throws Exception {
        String email1 = "ratelimit-ind-1-" + UUID.randomUUID() + "@test.com";
        String email2 = "ratelimit-ind-2-" + UUID.randomUUID() + "@test.com";
        String password = "password123";

        // Create user with email1
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user1 = User.builder()
                .email(email1)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test User 1")
                .phone("+1" + String.format("%010d", new java.util.Random().nextInt(1000000000)))
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.save(user1);

        // Create user with email2
        User user2 = User.builder()
                .email(email2)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test User 2")
                .phone("+1" + String.format("%010d", new java.util.Random().nextInt(1000000000)))
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        userRepository.save(user2);

        // Make 5 login attempts with email1 (should succeed)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    LoginRequest.builder().email(email1).password(password).build())))
                    .andExpect(status().isOk());
        }

        // 6th login with email1 should be rate limited
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(email1).password(password).build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // But login with email2 should still work (independent limit)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(email2).password(password).build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Rate limit resets after time window advances")
    void rateLimitResetsAfterTimeWindowAdvances() throws Exception {
        // Make 5 requests - should succeed
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    LoginRequest.builder().email(userEmail).password(password).build())))
                    .andExpect(status().isOk());
        }

        // 6th request - should be rate limited (429)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password(password).build())))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        // Advance clock past the 1-hour window
        mutableClock.advance(Duration.ofHours(1).plusMinutes(1));

        // 7th request after window reset - should succeed (200)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password(password).build())))
                .andExpect(status().isOk());
    }
}