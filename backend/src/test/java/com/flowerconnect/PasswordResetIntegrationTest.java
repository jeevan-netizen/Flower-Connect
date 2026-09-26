package com.flowerconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.PasswordResetToken;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.PasswordResetTokenRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ForgotPasswordRequest;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.ResetPasswordRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the password reset flow using real MySQL
 * and real Mailhog containers. Verifies that:
 * <ul>
 *   <li>Forgot-password for existing ACTIVE user creates a hashed token with 30-min TTL</li>
 *   <li>The reset email is actually delivered to Mailhog and contains the raw token in the link</li>
 *   <li>Valid reset token succeeds and marks token USED (not deleted)</li>
 *   <li>Successful reset revokes ALL refresh tokens for the user</li>
 *   <li>Expired/used/malformed/nonexistent tokens all return the same generic error</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private String userEmail;
    private String userPhone;
    private Long userId;

    @BeforeEach
    void setUp() {
        userEmail = "reset-test-" + UUID.randomUUID() + "@test.com";
        userPhone = "+1" + String.format("%010d", new java.util.Random().nextInt(1000000000));
        String password = "password123";

        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email(userEmail)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Reset Test User")
                .phone(userPhone)
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        User saved = userRepository.save(user);
        userId = saved.getId();

        // Clear Mailhog messages before each test
        clearMailhog();
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
        clearMailhog();
    }

    private void clearMailhog() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            int apiPort = MAILHOG.getMappedPort(8025);
            String deleteUrl = "http://" + MAILHOG.getHost() + ":" + apiPort + "/api/v1/messages";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deleteUrl))
                    .DELETE()
                    .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // Ignore cleanup failures
        }
    }

    private String getResetTokenFromMailhog() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        int apiPort = MAILHOG.getMappedPort(8025);
        String messagesUrl = "http://" + MAILHOG.getHost() + ":" + apiPort + "/api/v2/messages";

        // Wait for email to arrive (up to 10 seconds)
        String rawToken = null;
        for (int i = 0; i < 20; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(messagesUrl))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");
            if (items != null && items.isArray() && items.size() > 0) {
                for (JsonNode item : items) {
                    String body = item.get("Content").get("Body").asText();
                    // Extract token from reset link: baseUrl + "reset-password?token=" + rawToken
                    if (body.contains("reset-password?token=")) {
                        int idx = body.indexOf("reset-password?token=");
                        rawToken = body.substring(idx + "reset-password?token=".length());
                        // Token may have trailing newline or other chars
                        rawToken = rawToken.split("\\s")[0];
                        return rawToken;
                    }
                }
            }
            Thread.sleep(500);
        }
        return rawToken; // May be null if not found
    }

    @Test
    @DisplayName("Forgot password for existing ACTIVE user creates hashed token with 30-min TTL and delivers email")
    void forgotPasswordActiveUserCreatesTokenAndDeliversEmail() throws Exception {
        // Act
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(userEmail).build())))
                .andExpect(status().isOk());

        // Assert: Token created in DB with hash only (not raw)
        List<PasswordResetToken> tokens = resetTokenRepository.findByUserId(userId);
        assertEquals(1, tokens.size(), "Exactly one reset token should be created");
        PasswordResetToken token = tokens.get(0);
        assertNotNull(token.getTokenHash(), "Token hash should be persisted");
        assertNotEquals(token.getTokenHash(), "raw-token", "Raw token should not be stored");
        assertEquals(userId, token.getUser().getId());
        assertFalse(token.isUsed(), "New token should not be marked used");

        // Assert: Expiry is exactly 30 minutes from Clock
        LocalDateTime expectedExpiry = LocalDateTime.now(clock).plusMinutes(30);
        assertEquals(expectedExpiry.getMinute(), token.getExpiresAt().getMinute(),
                "Token expiry should be 30 minutes from Clock (minute precision)");

        // Assert: Email delivered to Mailhog with raw token in reset link
        String rawToken = getResetTokenFromMailhog();
        assertNotNull(rawToken, "Reset email should be delivered to Mailhog with raw token in link");

        // Verify the raw token matches the hash in DB
        String computedHash = com.flowerconnect.security.jwt.PasswordResetService.hashToken(rawToken);
        assertEquals(computedHash, token.getTokenHash(),
                "Raw token from email should hash to the same value stored in DB");
    }

    @Test
    @DisplayName("Forgot password for nonexistent email returns identical 200 and creates no token")
    void forgotPasswordNonexistentEmailReturnsIdentical200() throws Exception {
        String nonexistentEmail = "nonexistent-" + UUID.randomUUID() + "@test.com";

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(nonexistentEmail).build())))
                .andExpect(status().isOk());

        // No token should be created
        assertEquals(0, resetTokenRepository.count(), "No token should be created for nonexistent email");
    }

    @Test
    @DisplayName("Forgot password for SUSPENDED user returns identical 200 and creates no token")
    void forgotPasswordSuspendedUserReturnsIdentical200() throws Exception {
        User suspended = User.builder()
                .email("suspended-reset-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Suspended User")
                .phone("+19999999999")
                .role(roleRepository.findByName("CUSTOMER").orElseThrow())
                .status(User.Status.SUSPENDED)
                .build();
        userRepository.save(suspended);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(suspended.getEmail()).build())))
                .andExpect(status().isOk());

        // No token should be created for SUSPENDED user
        List<PasswordResetToken> tokens = resetTokenRepository.findByUser(suspended);
        assertTrue(tokens.isEmpty(), "No token should be created for SUSPENDED user");
    }

    @Test
    @DisplayName("Forgot password for DISABLED user returns identical 200 and creates no token")
    void forgotPasswordDisabledUserReturnsIdentical200() throws Exception {
        User disabled = User.builder()
                .email("disabled-reset-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Disabled User")
                .phone("+18888888888")
                .role(roleRepository.findByName("CUSTOMER").orElseThrow())
                .status(User.Status.DISABLED)
                .build();
        userRepository.save(disabled);

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(disabled.getEmail()).build())))
                .andExpect(status().isOk());

        // No token should be created for DISABLED user
        List<PasswordResetToken> tokens = resetTokenRepository.findByUser(disabled);
        assertTrue(tokens.isEmpty(), "No token should be created for DISABLED user");
    }

    @Test
    @DisplayName("Valid reset token succeeds, marks token USED, changes password hash, revokes all refresh tokens")
    void validResetTokenSucceedsAndRevokesAllRefreshTokens() throws Exception {
        // Arrange: Request reset to get token via Mailhog
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email(userEmail).build())))
                .andExpect(status().isOk());

        String rawToken = getResetTokenFromMailhog();
        assertNotNull(rawToken, "Raw token should be delivered via Mailhog");

        // Create a login session first to have refresh tokens to revoke
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password("password123").build())))
                .andExpect(status().isOk())
                .andReturn();

        // Verify user has refresh tokens before reset
        List<Map<String, Object>> tokensBefore = jdbcTemplate.queryForList(
                "SELECT id, revoked_at FROM refresh_tokens WHERE user_id = ?", userId);
        assertFalse(tokensBefore.isEmpty(), "User should have refresh tokens before reset");
        long activeTokensBefore = tokensBefore.stream()
                .filter(t -> t.get("revoked_at") == null)
                .count();
        assertTrue(activeTokensBefore > 0, "At least one active refresh token should exist");

        String originalPasswordHash = userRepository.findById(userId).orElseThrow().getPasswordHash();

        // Act: Reset password with valid token
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token(rawToken).newPassword("newPassword123").build())))
                .andExpect(status().isOk());

        // Assert: Token marked USED (not deleted)
        PasswordResetToken usedToken = resetTokenRepository.findByUserId(userId).get(0);
        assertTrue(usedToken.isUsed(), "Token should be marked used after successful reset");
        assertNotNull(usedToken.getUsedAt(), "usedAt should be set");

        // Assert: Password hash changed
        String newPasswordHash = userRepository.findById(userId).orElseThrow().getPasswordHash();
        assertNotEquals(originalPasswordHash, newPasswordHash, "Password hash should change after reset");
        assertTrue(passwordEncoder.matches("newPassword123", newPasswordHash), "New password should be valid");

        // Assert: ALL refresh tokens revoked
        List<Map<String, Object>> tokensAfter = jdbcTemplate.queryForList(
                "SELECT id, revoked_at FROM refresh_tokens WHERE user_id = ?", userId);
        long activeTokensAfter = tokensAfter.stream()
                .filter(t -> t.get("revoked_at") == null)
                .count();
        assertEquals(0, activeTokensAfter, "All refresh tokens should be revoked after password reset");

        // Assert: New password works for login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password("newPassword123").build())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Expired reset token is rejected with generic error")
    void expiredResetTokenRejected() throws Exception {
        // Create an expired token directly in DB
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .tokenHash(com.flowerconnect.security.jwt.PasswordResetService.hashToken("expired-raw-token"))
                .expiresAt(LocalDateTime.now(clock).minusMinutes(1)) // Already expired
                .build();
        resetTokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("expired-raw-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));

        // Token should still exist (not deleted)
        assertTrue(resetTokenRepository.findById(expiredToken.getId()).isPresent(),
                "Expired token should not be deleted");
    }

    @Test
    @DisplayName("Already-used reset token is rejected with generic error")
    void usedResetTokenRejected() throws Exception {
        // Create a used token
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .tokenHash(com.flowerconnect.security.jwt.PasswordResetService.hashToken("used-raw-token"))
                .expiresAt(LocalDateTime.now(clock).plusMinutes(30))
                .usedAt(LocalDateTime.now(clock))
                .build();
        resetTokenRepository.save(usedToken);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("used-raw-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));

        // Token should still exist and remain used
        PasswordResetToken token = resetTokenRepository.findById(usedToken.getId()).orElseThrow();
        assertTrue(token.isUsed());
    }

    @Test
    @DisplayName("Malformed reset token is rejected with generic error")
    void malformedResetTokenRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("not-a-valid-token-format").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    @DisplayName("Nonexistent reset token is rejected with generic error")
    void nonexistentResetTokenRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("nonexistent-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    @DisplayName("All invalid token cases return identical generic error response")
    void allInvalidTokenCasesReturnIdenticalError() throws Exception {
        // Create expired token
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .tokenHash(com.flowerconnect.security.jwt.PasswordResetService.hashToken("expired-token"))
                .expiresAt(LocalDateTime.now(clock).minusMinutes(1))
                .build();
        resetTokenRepository.save(expiredToken);

        // Create used token
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .user(userRepository.findById(userId).orElseThrow())
                .tokenHash(com.flowerconnect.security.jwt.PasswordResetService.hashToken("used-token"))
                .expiresAt(LocalDateTime.now(clock).plusMinutes(30))
                .usedAt(LocalDateTime.now(clock))
                .build();
        resetTokenRepository.save(usedToken);

        String[] invalidTokens = {"expired-token", "used-token", "malformed", "nonexistent"};
        for (String token : invalidTokens) {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    ResetPasswordRequest.builder().token(token).newPassword("newPassword123").build())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired reset token"))
                    .andReturn();

            // Verify response structure is identical
            String body = result.getResponse().getContentAsString();
            JsonNode json = objectMapper.readTree(body);
            assertEquals("Invalid or expired reset token", json.get("message").asText());
            assertTrue(json.has("code"));
            assertTrue(json.has("timestamp"));
        }
    }
}