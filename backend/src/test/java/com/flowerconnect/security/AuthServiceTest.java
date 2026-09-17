package com.flowerconnect.security;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.ResourceConflictException;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.*;
import com.flowerconnect.security.jwt.JwtService;
import com.flowerconnect.security.jwt.RefreshTokenService;
import com.flowerconnect.security.jwt.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private Role customerRole;
    private User user;

    @BeforeEach
    void setUp() {
        customerRole = Role.builder().id(1L).name("CUSTOMER").build();
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("$2a$10$encoded")
                .fullName("Test User")
                .phone("+1234567890")
                .role(customerRole)
                .active(true)
                .build();
    }

    @Test
    void shouldRegisterUserWithCustomerRole() {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com")
                .password("password123")
                .fullName("New User")
                .phone("+1234567890")
                .build();

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProperties.getAccessTtlMs()).thenReturn(900000L);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900000L, response.getExpiresIn());

        verify(userRepository).existsByEmail("new@test.com");
        verify(roleRepository).findByName("CUSTOMER");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateAccessToken(eq("new@test.com"), eq("CUSTOMER"), any());
        verify(refreshTokenService).createRefreshToken(1L);
    }

    @Test
    void shouldThrowConflictWhenEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@test.com")
                .password("password123")
                .fullName("Existing User")
                .build();

        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginSuccessfully() {
        LoginRequest request = LoginRequest.builder()
                .email("user@test.com")
                .password("password123")
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.fromUser(user);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProperties.getAccessTtlMs()).thenReturn(900000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByIdWithRole(1L);
    }

    @Test
    void shouldThrowBadCredentialsOnLoginFailure() {
        LoginRequest request = LoginRequest.builder()
                .email("user@test.com")
                .password("wrongpassword")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.login(request));
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    void shouldRefreshTokens() {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("old-refresh-token")
                .build();

        when(refreshTokenService.validateAndReturnUser("old-refresh-token")).thenReturn(user);
        when(refreshTokenService.rotateRefreshToken("old-refresh-token")).thenReturn("new-refresh-token");
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("new-access-token");
        when(jwtProperties.getAccessTtlMs()).thenReturn(900000L);

        AuthResponse response = authService.refresh(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());

        verify(refreshTokenService).validateAndReturnUser("old-refresh-token");
        verify(refreshTokenService).rotateRefreshToken("old-refresh-token");
    }

    @Test
    void shouldLogoutAndRevokeToken() {
        RefreshRequest request = RefreshRequest.builder()
                .refreshToken("refresh-token-to-revoke")
                .build();

        authService.logout(request);

        verify(refreshTokenService).revokeRefreshToken("refresh-token-to-revoke");
    }

    @Test
    void shouldUseDefaultRoleWhenUserRoleIsNull() {
        User userWithoutRole = User.builder()
                .id(2L)
                .email("user2@test.com")
                .passwordHash("$2a$10$encoded")
                .fullName("User Two")
                .role(null)
                .active(true)
                .build();

        LoginRequest request = LoginRequest.builder()
                .email("user2@test.com")
                .password("password123")
                .build();

        UserDetailsImpl userDetails = UserDetailsImpl.fromUser(userWithoutRole);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(userRepository.findByIdWithRole(2L)).thenReturn(Optional.of(userWithoutRole));
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(2L)).thenReturn("refresh-token");
        when(jwtProperties.getAccessTtlMs()).thenReturn(900000L);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        verify(jwtService).generateAccessToken(eq("user2@test.com"), eq("CUSTOMER"), any());
    }
}
