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
import com.flowerconnect.test.IntegrationTestBase;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1"
})
class AuthApiIntegrationTest extends IntegrationTestBase {

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

    @BeforeEach
    void setUp() {
        userRepository.findByEmail(userEmail).ifPresent(userRepository::delete);
        userRepository.findByEmail(duplicatePhoneEmail).ifPresent(userRepository::delete);
        accessToken = null;
        refreshToken = null;
    }

    @AfterEach
    void tearDown() {
        userRepository.findByEmail(userEmail).ifPresent(user -> {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", user.getId());
            userRepository.delete(user);
        });
        userRepository.findByEmail(duplicatePhoneEmail).ifPresent(user -> {
            jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", user.getId());
            userRepository.delete(user);
        });
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidTokenOnProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer invalid-token-here"))
                .andExpect(status().isUnauthorized());
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

    private void setupUser() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email(userEmail)
                .passwordHash(passwordEncoder.encode("password123"))
                .fullName("API Test User")
                .phone("+1234567890")
                .role(role)
                .active(true)
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
