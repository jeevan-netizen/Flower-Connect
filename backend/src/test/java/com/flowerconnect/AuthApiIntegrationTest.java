package com.flowerconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.RefreshRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.security.jwt.RefreshTokenService;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest extends AbstractIntegrationTest {

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

    private String accessToken;
    private String refreshToken;
    private final String userEmail = "apitest@test.com";
    private final String duplicatePhoneEmail = "duplicate-phone@test.com";
    private final String suspendedEmail = "suspended@test.com";
    private final String disabledEmail = "disabled@test.com";

    @BeforeEach
    void setUp() {
        // Clean only test-specific data by email
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?)",
                userEmail, duplicatePhoneEmail, suspendedEmail, disabledEmail);
        accessToken = null;
        refreshToken = null;
    }

    @AfterEach
    void tearDown() {
        // Clean only test-specific data by email (refresh_tokens cascade via FK)
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?)",
                userEmail, duplicatePhoneEmail, suspendedEmail, disabledEmail);
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
                .andExpect(jsonPath("$.expiresIn").value(900000));

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
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
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
    void shouldRefreshTokensAndRotateOldToken() throws Exception {
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
    void shouldRejectReusedRefreshTokenAfterRotation() throws Exception {
        setupUserAndLogin();
        String oldRefreshToken = refreshToken;

        RefreshRequest request = RefreshRequest.builder()
                .refreshToken(oldRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
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
    void shouldLogoutAndRevokeRefreshToken() throws Exception {
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
