package com.flowerconnect.security.jwt;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.RefreshTokenRepository;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("$2a$10$encoded")
                .fullName("Test User")
                .role(role)
                .active(true)
                .build();
    }

    @Test
    void shouldCreateRefreshTokenReturnsRawToken() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateRefreshToken()).thenReturn("raw-token-string");
        when(jwtProperties.getRefreshTtlMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(1L);

        assertNotNull(rawToken);
        assertEquals("raw-token-string", rawToken);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void shouldCreateRefreshTokenThrowsWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.createRefreshToken(999L));
    }

    @Test
    void shouldRevokeRefreshToken() {
        String rawToken = "raw-refresh-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.revokeRefreshToken(rawToken);

        assertTrue(storedToken.isRevoked());
        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    void shouldNotFailWhenRevokingUnknownToken() {
        String rawToken = "unknown-token";
        String hash = refreshTokenService.hashToken(rawToken);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        refreshTokenService.revokeRefreshToken(rawToken);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void shouldValidateAndReturnUserForValidToken() {
        String rawToken = "valid-raw-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));

        User result = refreshTokenService.validateAndReturnUser(rawToken);

        assertEquals(user, result);
    }

    @Test
    void shouldThrowWhenValidatingRevokedToken() {
        String rawToken = "revoked-raw-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.validateAndReturnUser(rawToken));
    }

    @Test
    void shouldThrowWhenValidatingExpiredToken() {
        String rawToken = "expired-raw-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.validateAndReturnUser(rawToken));
        verify(refreshTokenRepository).delete(storedToken);
    }

    @Test
    void shouldThrowWhenValidatingUnknownToken() {
        String rawToken = "unknown-token";
        String hash = refreshTokenService.hashToken(rawToken);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.validateAndReturnUser(rawToken));
    }

    @Test
    void shouldRotateRefreshToken() {
        String oldRawToken = "old-token";
        String oldHash = refreshTokenService.hashToken(oldRawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(oldHash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateRefreshToken()).thenReturn("new-raw-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtProperties.getRefreshTtlMs()).thenReturn(604800000L);

        String newRawToken = refreshTokenService.rotateRefreshToken(oldRawToken);

        assertNotNull(newRawToken);
        assertEquals("new-raw-token", newRawToken);
        assertTrue(storedToken.isRevoked());
        verify(refreshTokenRepository).save(storedToken);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void shouldThrowWhenRotatingRevokedToken() {
        String rawToken = "revoked-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.rotateRefreshToken(rawToken));
    }

    @Test
    void shouldThrowWhenRotatingExpiredToken() {
        String rawToken = "expired-token";
        String hash = refreshTokenService.hashToken(rawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));
        doNothing().when(refreshTokenRepository).delete(any());

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.rotateRefreshToken(rawToken));
    }

    @Test
    void shouldThrowWhenRotatingUnknownToken() {
        String rawToken = "unknown-token";
        String hash = refreshTokenService.hashToken(rawToken);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThrows(TokenRefreshException.class, () -> refreshTokenService.rotateRefreshToken(rawToken));
    }

    @Test
    void shouldRevokeAllForUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doNothing().when(refreshTokenRepository).revokeAllActiveTokensForUser(user);

        refreshTokenService.revokeAllRefreshTokensForUser(1L);

        verify(refreshTokenRepository).revokeAllActiveTokensForUser(user);
    }

    @Test
    void shouldHashTokenConsistently() {
        String rawToken = "test-token";
        String hash1 = refreshTokenService.hashToken(rawToken);
        String hash2 = refreshTokenService.hashToken(rawToken);

        assertEquals(hash1, hash2);
        assertNotEquals(rawToken, hash1);
    }

    @Test
    void shouldCleanupExpiredTokens() {
        when(refreshTokenRepository.deleteExpiredAndRevokedBefore(any(LocalDateTime.class))).thenReturn(5);

        refreshTokenService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredAndRevokedBefore(any(LocalDateTime.class));
    }

    @Test
    void shouldCleanupExpiredTokensNoDeletions() {
        when(refreshTokenRepository.deleteExpiredAndRevokedBefore(any(LocalDateTime.class))).thenReturn(0);

        refreshTokenService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredAndRevokedBefore(any(LocalDateTime.class));
    }
}
