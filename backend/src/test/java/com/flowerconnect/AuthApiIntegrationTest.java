package com.flowerconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.test.MutableClock;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.ChangePasswordRequest;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.ProfileUpdateRequest;
import com.flowerconnect.security.dto.RefreshRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.security.jwt.RefreshTokenService;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_HEADER_NAME = "X-FlowerConnect-Client";
    private static final String CLIENT_HEADER_VALUE = "1";

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
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableClock clock;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private CorsProperties corsProperties;

    private String accessToken;
    private String refreshToken;
    private final String userEmail = "apitest@test.com";
    private final String duplicatePhoneEmail = "duplicate-phone@test.com";
    private final String suspendedEmail = "suspended@test.com";
    private final String disabledEmail = "disabled@test.com";

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?)",
                userEmail, duplicatePhoneEmail, suspendedEmail, disabledEmail);
        accessToken = null;
        refreshToken = null;
        clock.setInstant(Instant.now());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?)",
                userEmail, duplicatePhoneEmail, suspendedEmail, disabledEmail);
        clock.setInstant(Instant.now());
    }

    @Test
    void shouldRegisterNewUserWithCustomerRole() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(userEmail)
                .password("password123")
                .fullName("API Test User")
                .phone("+1234567890")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900000))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
                .andExpect(header().string("Set-Cookie", appProperties.isRefreshCookieSecure()
                        ? containsString("Secure")
                        : org.hamcrest.Matchers.not(containsString("Secure"))));

        User saved = userRepository.findByEmail(userEmail).orElseThrow();
        assertEquals("CUSTOMER", saved.getRole().getName());
    }

    @Test
    void shouldRejectDuplicateRegistrationWith409() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(userEmail)
                .password("password123")
                .fullName("API Test User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void shouldRejectDuplicateRegistrationWith409ForDuplicatePhone() throws Exception {
        RegisterRequest firstRequest = RegisterRequest.builder()
                .email(userEmail)
                .password("password123")
                .fullName("API Test User")
                .phone("+1234567890")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isOk());

        RegisterRequest secondRequest = RegisterRequest.builder()
                .email(duplicatePhoneEmail)
                .password("password123")
                .fullName("Duplicate Phone User")
                .phone("+1234567890")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number already in use"));

        assertFalse(userRepository.findByEmail(duplicatePhoneEmail).isPresent(),
                "Second user with duplicate phone should not be persisted");
    }

    @Test
    void shouldRejectInvalidRegistrationData() throws Exception {
        String invalidJson = """
                {"email":"not-an-email","password":"123","fullName":""}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLoginAndReturnTokens() throws Exception {
        setupUser();
        LoginRequest request = LoginRequest.builder()
                .email(userEmail)
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().string("Set-Cookie", containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Path=/api/v1/auth")))
                .andExpect(header().string("Set-Cookie", appProperties.isRefreshCookieSecure()
                        ? containsString("Secure")
                        : org.hamcrest.Matchers.not(containsString("Secure"))));
    }

    @Test
    void shouldRejectInvalidCredentialsWith401() throws Exception {
        setupUser();
        LoginRequest request = LoginRequest.builder()
                .email(userEmail)
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldReturnGenericErrorForNonExistentUser() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldReturn403ForSuspendedUserWithCorrectPassword() throws Exception {
        setupSuspendedUser();
        LoginRequest request = LoginRequest.builder()
                .email("suspended@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("Account suspended"));
    }

    @Test
    void shouldReturn401ForDisabledUserWithCorrectPassword() throws Exception {
        setupDisabledUser();
        LoginRequest request = LoginRequest.builder()
                .email("disabled@test.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldReturnGenericErrorForSuspendedUserWithWrongPassword() throws Exception {
        setupSuspendedUser();
        LoginRequest request = LoginRequest.builder()
                .email("suspended@test.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldReturnGenericErrorForDisabledUserWithWrongPassword() throws Exception {
        setupDisabledUser();
        LoginRequest request = LoginRequest.builder()
                .email("disabled@test.com")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldRejectAccessTokenForUserSuspendedAfterLogin() throws Exception {
        setupUserAndLogin();
        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(com.flowerconnect.domain.User.Status.SUSPENDED);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.message").value("Account suspended"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRejectAccessTokenForDisabledUserOnProtectedEndpoint() throws Exception {
        setupUserAndLogin();
        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(com.flowerconnect.domain.User.Status.DISABLED);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRejectRefreshForSuspendedUser() throws Exception {
        setupUserAndLogin();
        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        // Suspend the user after login
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(com.flowerconnect.domain.User.Status.SUSPENDED);
        userRepository.save(user);

        // Try to refresh with old refresh token
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account suspended"));
    }

    @Test
    void shouldRejectRefreshForDisabledUser() throws Exception {
        setupUserAndLogin();
        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        // Disable the user after login
        User user = userRepository.findById(userId).orElseThrow();
        user.setStatus(com.flowerconnect.domain.User.Status.DISABLED);
        userRepository.save(user);

        // Try to refresh with old refresh token
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void shouldAccessProtectedEndpointWithValidToken() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.fullName").value("API Test User"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void shouldRejectMissingTokenOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldRejectInvalidTokenOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token-here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403WithCodeForAccessDenied() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(get("/actuator/metrics")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldAllowBodyOnlyRefreshWithoutClientHeaderOrOrigin() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").value(org.hamcrest.Matchers.not(oldRefreshToken)));
    }

    @Test
    void shouldAllowRapidRefreshWithinGraceWindow() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        MvcResult firstRefreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String firstResponseBody = firstRefreshResult.getResponse().getContentAsString();
        AuthResponse firstRefreshResponse = objectMapper.readValue(firstResponseBody, AuthResponse.class);
        String firstNewRefreshToken = firstRefreshResponse.getRefreshToken();

        // Second refresh with the SAME old (now-revoked) token within grace window
        MvcResult secondRefreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String secondResponseBody = secondRefreshResult.getResponse().getContentAsString();
        AuthResponse secondRefreshResponse = objectMapper.readValue(secondResponseBody, AuthResponse.class);
        String secondNewRefreshToken = secondRefreshResponse.getRefreshToken();

        // Each reuse within grace gets its own valid new token (not the same cached token)
        assertNotEquals(firstNewRefreshToken, secondNewRefreshToken,
                "Two reuses within grace should get different new tokens");

        // Both new tokens must still be valid (usable for another refresh)
        RefreshRequest refreshRequest1 = RefreshRequest.builder()
                .refreshToken(firstNewRefreshToken)
                .build();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        RefreshRequest refreshRequest2 = RefreshRequest.builder()
                .refreshToken(secondNewRefreshToken)
                .build();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void shouldRejectReusedRefreshTokenAfterRotationOutsideGraceWindow() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        clock.advance(java.time.Duration.ofSeconds(12));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected outside grace window"));
    }

    @Test
    void shouldRevokeSiblingTokensWhenReusedOutsideGraceWindow() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        List<Map<String, Object>> tokensBeforeReuse = jdbcTemplate.queryForList(
                "SELECT id, revoked_at, replaced_by_id, family_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at",
                userId);
        assertEquals(2, tokensBeforeReuse.size());

        Long siblingTokenId = ((Number) tokensBeforeReuse.get(1).get("id")).longValue();
        assertNull(tokensBeforeReuse.get(1).get("revoked_at"),
                "Sibling token (new refresh token) should still be live before reuse");

        clock.advance(java.time.Duration.ofSeconds(12));

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token reuse detected outside grace window"));

        List<Map<String, Object>> tokensAfterReuse = jdbcTemplate.queryForList(
                "SELECT id, revoked_at FROM refresh_tokens WHERE user_id = ? ORDER BY created_at",
                userId);
        assertEquals(2, tokensAfterReuse.size());

        Map<String, Object> siblingTokenAfter = tokensAfterReuse.get(1);
        assertEquals(siblingTokenId, ((Number) siblingTokenAfter.get("id")).longValue());
        assertNotNull(siblingTokenAfter.get("revoked_at"),
                "Sibling token in same family should be revoked by revokeAllTokensInFamily");
    }

    @Test
    void shouldRejectReuseOfLoggedOutTokenWhenChainDeadEndsAtLogout() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest.builder()
                        .refreshToken(oldRefreshToken)
                        .build())))
                .andExpect(status().isOk())
                .andReturn();

        String liveRefreshToken = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest.builder()
                        .refreshToken(oldRefreshToken)
                        .build())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        AuthResponse refreshResponse = objectMapper.readValue(liveRefreshToken, AuthResponse.class);
        liveRefreshToken = refreshResponse.getRefreshToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest.builder()
                        .refreshToken(liveRefreshToken)
                        .build())))
                .andExpect(status().isNoContent());

        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();

        List<Map<String, Object>> tokensBefore = jdbcTemplate.queryForList(
                "SELECT id, revoked_at, replaced_by_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at",
                userId);
        Map<String, Object> loggedOutToken = tokensBefore.get(tokensBefore.size() - 1);
        assertNotNull(loggedOutToken.get("revoked_at"),
                "Logged-out token should have revoked_at set");
        assertNull(loggedOutToken.get("replaced_by_id"),
                "Logged-out token should have replaced_by_id = null");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RefreshRequest.builder()
                        .refreshToken(oldRefreshToken)
                        .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token has been revoked"));

        List<Map<String, Object>> tokensAfter = jdbcTemplate.queryForList(
                "SELECT id, revoked_at, replaced_by_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at",
                userId);
        assertEquals(tokensBefore.size(), tokensAfter.size());
        for (int i = 0; i < tokensBefore.size(); i++) {
            assertEquals(tokensBefore.get(i).get("revoked_at"), tokensAfter.get(i).get("revoked_at"),
                    "No additional tokens should be revoked by family revocation after reuse of logged-out chain");
        }
    }

    @Test
    void shouldRejectMalformedRefreshToken() throws Exception {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("malformed-token-not-in-db")
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowBodyOnlyLogoutWithoutClientHeaderOrOriginAndRevoke() throws Exception {
        setupUserAndLogin();

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRegisterLoginRefreshAndLogoutWithDatabaseAssertions() throws Exception {
        String testEmail = "integration-" + UUID.randomUUID() + "@test.com";
        String testPhone = "+1" + String.format("%010d", new java.util.Random().nextInt(1000000000));
        String testPassword = "password123";

        // 1. Register
        RegisterRequest registerRequest = RegisterRequest.builder()
                .email(testEmail)
                .password(testPassword)
                .fullName("Integration Test User")
                .phone(testPhone)
                .build();

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = registerResult.getResponse().getContentAsString();
        AuthResponse registerResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String initialAccessToken = registerResponse.getAccessToken();
        String initialRefreshToken = registerResponse.getRefreshToken();

        // Verify user created with lowercase email
        User savedUser = userRepository.findByEmail(testEmail).orElseThrow();
        assertEquals(testEmail.toLowerCase(), savedUser.getEmail());
        Long userId = savedUser.getId();

        // Verify initial refresh token in DB
        List<Map<String, Object>> initialTokens = jdbcTemplate.queryForList(
                "SELECT id, token_hash, revoked_at, replaced_by_id, family_id FROM refresh_tokens WHERE user_id = ?", userId);
        assertEquals(1, initialTokens.size());
        Long initialTokenId = ((Number) initialTokens.get(0).get("id")).longValue();
        assertNull(initialTokens.get(0).get("revoked_at"));
        assertNull(initialTokens.get(0).get("replaced_by_id"));
        String familyId = (String) initialTokens.get(0).get("family_id");
        assertNotNull(familyId);

        // 2. Login
        LoginRequest loginRequest = LoginRequest.builder()
                .email(testEmail)
                .password(testPassword)
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        responseBody = loginResult.getResponse().getContentAsString();
        AuthResponse loginResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String loginAccessToken = loginResponse.getAccessToken();
        String loginRefreshToken = loginResponse.getRefreshToken();

        // Verify new refresh token created (login creates new token with NEW family_id)
        List<Map<String, Object>> loginTokens = jdbcTemplate.queryForList(
                "SELECT id, token_hash, revoked_at, replaced_by_id, family_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at", userId);
        assertEquals(2, loginTokens.size());
        Long loginTokenId = ((Number) loginTokens.get(1).get("id")).longValue();
        assertNull(loginTokens.get(1).get("revoked_at"));
        assertNull(loginTokens.get(1).get("replaced_by_id"));
        String loginFamilyId = (String) loginTokens.get(1).get("family_id");
        assertNotNull(loginFamilyId);
        assertNotEquals(familyId, loginFamilyId, "Login should create new family_id");

        // 3. Refresh (rotate)
        RefreshRequest refreshRequest = RefreshRequest.builder()
                .refreshToken(loginRefreshToken)
                .build();

        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        responseBody = refreshResult.getResponse().getContentAsString();
        AuthResponse refreshResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String newAccessToken = refreshResponse.getAccessToken();
        String newRefreshToken = refreshResponse.getRefreshToken();

        // Verify rotation: old token revoked, new token created with same family_id as login
        List<Map<String, Object>> refreshTokens = jdbcTemplate.queryForList(
                "SELECT id, token_hash, revoked_at, replaced_by_id, family_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at", userId);
        assertEquals(3, refreshTokens.size());

        // Old token (login token) should be revoked and have replaced_by_id pointing to new token
        Map<String, Object> oldTokenRow = refreshTokens.get(1);
        assertNotNull(oldTokenRow.get("revoked_at"));
        assertEquals(loginTokenId, oldTokenRow.get("id"));
        assertEquals(((Number) refreshTokens.get(2).get("id")).longValue(), oldTokenRow.get("replaced_by_id"));
        assertEquals(loginFamilyId, oldTokenRow.get("family_id"));

        // New token should have same family_id as login token and no revoked_at
        Map<String, Object> newTokenRow = refreshTokens.get(2);
        assertNull(newTokenRow.get("revoked_at"));
        assertNull(newTokenRow.get("replaced_by_id"));
        assertEquals(loginFamilyId, newTokenRow.get("family_id"));
        Long newTokenId = ((Number) newTokenRow.get("id")).longValue();

        // 4. Logout
        RefreshRequest logoutRequest = RefreshRequest.builder()
                .refreshToken(newRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isNoContent());

        // Verify logout sets revoked_at on current token
        List<Map<String, Object>> logoutTokens = jdbcTemplate.queryForList(
                "SELECT id, token_hash, revoked_at, replaced_by_id, family_id FROM refresh_tokens WHERE user_id = ? ORDER BY created_at", userId);
        assertEquals(3, logoutTokens.size());

        Map<String, Object> currentTokenRow = logoutTokens.get(2);
        assertEquals(newTokenId, currentTokenRow.get("id"));
        assertNotNull(currentTokenRow.get("revoked_at"));
        assertNull(currentTokenRow.get("replaced_by_id"));
        assertEquals(loginFamilyId, currentTokenRow.get("family_id"));

        // Cleanup
        jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
        userRepository.delete(savedUser);
    }

    @Test
    void shouldSetRefreshCookieOnRefresh() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")));
    }

    @Test
    void shouldRejectCookieRefreshWithoutClientHeader() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(HttpHeaders.ORIGIN, allowedOrigin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid client header"));
    }

    @Test
    void shouldRejectCookieLogoutWithoutClientHeader() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(HttpHeaders.ORIGIN, allowedOrigin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid client header"));
    }

    @Test
    void shouldRejectCookieRefreshWithMismatchedClientHeader() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, "invalid")
                        .header(HttpHeaders.ORIGIN, allowedOrigin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid client header"));
    }

    @Test
    void shouldRejectCookieLogoutWithMismatchedClientHeader() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, "invalid")
                        .header(HttpHeaders.ORIGIN, allowedOrigin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid client header"));
    }

    @Test
    void shouldRejectCookieRefreshWithoutOrigin() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid request origin"));
    }

    @Test
    void shouldRejectCookieLogoutWithoutOrigin() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid request origin"));
    }

    @Test
    void shouldRejectCookieRefreshWithMismatchedOrigin() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE)
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid CORS request"));
    }

    @Test
    void shouldRejectCookieLogoutWithMismatchedOrigin() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE)
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Invalid CORS request"));
    }

    @Test
    void shouldRefreshUsingCookieWithClientHeaderAndAllowedOrigin() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE)
                        .header(HttpHeaders.ORIGIN, allowedOrigin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponse response = objectMapper.readValue(responseBody, AuthResponse.class);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertNotEquals(oldRefreshToken, response.getRefreshToken(),
                "Refresh via cookie should rotate to a new token");
    }

    @Test
    void shouldCookieTakePrecedenceOverBodyWhenBothPresent() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("wrong-token-in-body")
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", oldRefreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE)
                        .header(HttpHeaders.ORIGIN, allowedOrigin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldClearCookieOnLogoutWithClientHeaderAndAllowedOrigin() throws Exception {
        setupUserAndLogin();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", refreshToken))
                        .header(CLIENT_HEADER_NAME, CLIENT_HEADER_VALUE)
                        .header(HttpHeaders.ORIGIN, allowedOrigin()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void shouldRejectRefreshWhenEmptyBodyAndNoCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateProfileWithValidFullNameAndPhone() throws Exception {
        setupUserAndLogin();

        String newPhone = "+1" + String.format("%010d", new java.util.Random().nextInt(1000000000));
        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ProfileUpdateRequest.builder()
                        .fullName("Updated Name")
                        .phone(newPhone)
                        .build());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value(newPhone))
                .andExpect(jsonPath("$.email").value(userEmail));

        // Verify persisted
        User saved = userRepository.findByEmail(userEmail).orElseThrow();
        assertEquals("Updated Name", saved.getFullName());
        assertEquals(newPhone, saved.getPhone());
    }

    @Test
    void shouldRejectProfileUpdateWithInvalidPhoneFormat() throws Exception {
        setupUserAndLogin();

        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ProfileUpdateRequest.builder()
                        .phone("invalid-format")
                        .build());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectProfileUpdateWithDuplicatePhone() throws Exception {
        // Create another user with a phone
        String otherPhone = "+1" + String.format("%010d", new java.util.Random().nextInt(1000000000));
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User otherUser = User.builder()
                .email("other-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Other User")
                .phone(otherPhone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        userRepository.save(otherUser);

        setupUserAndLogin();

        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ProfileUpdateRequest.builder()
                        .phone(otherPhone)
                        .build());

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number already in use"));
    }

    @Test
    void shouldIgnoreEmailFieldInProfileUpdate() throws Exception {
        setupUserAndLogin();

        String requestJson = """
                {"fullName":"Updated","phone":"+19876543210","email":"attacker@test.com"}
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail));

        User saved = userRepository.findByEmail(userEmail).orElseThrow();
        assertEquals(userEmail, saved.getEmail(), "Email should not change");
    }

    @Test
    void shouldIgnoreUnknownFieldsInProfileUpdate() throws Exception {
        setupUserAndLogin();

        String requestJson = """
                {"fullName":"Updated","unknownField":"ignored"}
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated"));
    }

    @Test
    void shouldRejectChangePasswordWithWrongCurrentPassword() throws Exception {
        setupUserAndLogin();

        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ChangePasswordRequest.builder()
                        .currentPassword("wrongpassword")
                        .newPassword("newPassword123")
                        .build());

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void shouldChangePasswordSuccessfullyWithCorrectCurrentPassword() throws Exception {
        setupUserAndLogin();
        String originalPasswordHash = userRepository.findByEmail(userEmail).orElseThrow().getPasswordHash();

        // Verify user has refresh tokens before change
        Long userId = userRepository.findByEmail(userEmail).orElseThrow().getId();
        List<Map<String, Object>> tokensBefore = jdbcTemplate.queryForList(
                "SELECT id, revoked_at FROM refresh_tokens WHERE user_id = ?", userId);
        assertFalse(tokensBefore.isEmpty(), "User should have refresh tokens before password change");

        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ChangePasswordRequest.builder()
                        .currentPassword("password123")
                        .newPassword("newPassword123")
                        .build());

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated"));

        // Verify password hash changed
        String newPasswordHash = userRepository.findByEmail(userEmail).orElseThrow().getPasswordHash();
        assertNotEquals(originalPasswordHash, newPasswordHash, "Password hash should change");
        assertTrue(passwordEncoder.matches("newPassword123", newPasswordHash), "New password should be valid");

        // Verify ALL refresh tokens revoked
        List<Map<String, Object>> tokensAfter = jdbcTemplate.queryForList(
                "SELECT id, revoked_at FROM refresh_tokens WHERE user_id = ?", userId);
        long activeTokensAfter = tokensAfter.stream()
                .filter(t -> t.get("revoked_at") == null)
                .count();
        assertEquals(0, activeTokensAfter, "All refresh tokens should be revoked after password change");

        // Verify new password works for login
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().email(userEmail).password("newPassword123").build())))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectChangePasswordWithShortNewPassword() throws Exception {
        setupUserAndLogin();

        String requestJson = objectMapper.writeValueAsString(
                com.flowerconnect.security.dto.ChangePasswordRequest.builder()
                        .currentPassword("password123")
                        .newPassword("short")
                        .build());

        mockMvc.perform(post("/api/v1/users/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private String allowedOrigin() {
        return Arrays.stream(corsProperties.getOrigins().split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private void setupUser() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniquePhone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email(userEmail)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("API Test User")
                .phone(uniquePhone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        userRepository.save(user);
    }

    private void setupSuspendedUser() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniquePhone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email("suspended@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Suspended User")
                .phone(uniquePhone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.SUSPENDED)
                .build();
        userRepository.save(user);
    }

    private void setupDisabledUser() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniquePhone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email("disabled@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("Disabled User")
                .phone(uniquePhone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.DISABLED)
                .build();
        userRepository.save(user);
    }

    private void setupUserAndLogin() throws Exception {
        setupUser();
        LoginRequest request = LoginRequest.builder()
                .email(userEmail)
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        accessToken = authResponse.getAccessToken();
        refreshToken = authResponse.getRefreshToken();
    }
}
