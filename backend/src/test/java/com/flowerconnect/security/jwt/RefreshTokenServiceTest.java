package com.flowerconnect.security.jwt;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.RefreshTokenRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.test.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

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
    @Mock
    private Clock clock;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2025, 1, 15, 10, 0, 0);

    @BeforeEach
    void setUp() {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("$2a$10$encoded")
                .fullName("Test User")
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        Instant fixedInstant = FIXED_TIME.toInstant(ZoneOffset.UTC);
        lenient().when(clock.instant()).thenReturn(fixedInstant);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
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
        verify(refreshTokenRepository).save(argThat(t -> t.getFamilyId() != null));
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
                .expiresAt(FIXED_TIME.plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.revokeRefreshToken(rawToken);

        assertTrue(storedToken.isRevoked());
        assertNotNull(storedToken.getRevokedAt());
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
                .expiresAt(FIXED_TIME.plusDays(7))

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
                .expiresAt(FIXED_TIME.plusDays(7))
                .revokedAt(FIXED_TIME)
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
                .expiresAt(FIXED_TIME.minusDays(1))

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
                .familyId("test-family")
                .expiresAt(FIXED_TIME.plusDays(7))
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
        assertEquals("test-family", storedToken.getFamilyId());
        assertNotNull(storedToken.getRevokedAt());
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
                .expiresAt(FIXED_TIME.plusDays(7))
                .revokedAt(FIXED_TIME)
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
                .expiresAt(FIXED_TIME.minusDays(1))

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

    @Test
    void shouldGenerateFamilyIdOnLogin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtService.generateRefreshToken()).thenReturn("raw-token");
        when(jwtProperties.getRefreshTtlMs()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.createRefreshToken(1L);

        verify(refreshTokenRepository).save(argThat(t -> t.getFamilyId() != null && !t.getFamilyId().isEmpty()));
    }

    @Test
    void shouldPreserveFamilyIdOnRotation() {
        String oldRawToken = "old-token";
        String oldHash = refreshTokenService.hashToken(oldRawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(oldHash)
                .familyId("same-family-id")
                .expiresAt(FIXED_TIME.plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateRefreshToken()).thenReturn("new-raw-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtProperties.getRefreshTtlMs()).thenReturn(604800000L);

        refreshTokenService.rotateRefreshToken(oldRawToken);

        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        verify(refreshTokenRepository).save(argThat(t -> "same-family-id".equals(t.getFamilyId()) && t.getRevokedAt() == null));
    }

    @Test
    void shouldSetReplacedByIdOnRotation() {
        String oldRawToken = "old-token";
        String oldHash = refreshTokenService.hashToken(oldRawToken);

        RefreshToken storedToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(oldHash)
                .familyId("fam")
                .expiresAt(FIXED_TIME.plusDays(7))
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> {
            RefreshToken arg = inv.getArgument(0);
            if (arg.getId() == null) {
                arg.setId(2L);
            }
            return arg;
        });
        when(jwtService.generateRefreshToken()).thenReturn("new-raw-token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtProperties.getRefreshTtlMs()).thenReturn(604800000L);

        refreshTokenService.rotateRefreshToken(oldRawToken);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());

        RefreshToken newToken = captor.getAllValues().get(0);
        RefreshToken oldToken = captor.getAllValues().get(1);

        assertEquals(storedToken.getId(), oldToken.getId());
        assertEquals(newToken.getId(), oldToken.getReplacedById());
        assertEquals(storedToken.getFamilyId(), newToken.getFamilyId());
        assertNotNull(oldToken.getRevokedAt());
        assertNull(newToken.getRevokedAt());
    }

    @Test
    void shouldDetectExpiryWhenClockMovesPastExpiresAt() {
        MutableClock mutableClock = new MutableClock(FIXED_TIME.toInstant(ZoneOffset.UTC));
        RefreshTokenService service = new RefreshTokenService(
                refreshTokenRepository, userRepository, jwtService, jwtProperties, mutableClock);

        String rawToken = "raw";
        String hash = service.hashToken(rawToken);

        RefreshToken token = RefreshToken.builder()
                .id(1L)
                .user(user)
                .tokenHash(hash)
                .expiresAt(FIXED_TIME.plusHours(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        assertEquals(user, service.validateAndReturnUser(rawToken));

        mutableClock.advance(Duration.ofHours(2));

        assertThrows(TokenRefreshException.class, () -> service.validateAndReturnUser(rawToken));
    }
}
