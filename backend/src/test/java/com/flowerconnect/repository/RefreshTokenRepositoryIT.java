package com.flowerconnect.repository;

import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryIT extends IntegrationTestBase {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User createTestUser(String email) {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .role(role)
                .active(true)
                .build();
        return userRepository.save(user);
    }

    @Test
    void shouldSaveAndFindByTokenHash() {
        User user = createTestUser("rt@test.com");
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("sha256-hash-value")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.save(token);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("sha256-hash-value");

        assertTrue(found.isPresent());
        assertEquals(token.getId(), found.get().getId());
    }

    @Test
    void shouldEnforceUniqueTokenHash() {
        User user1 = createTestUser("rt1@test.com");
        User user2 = createTestUser("rt2@test.com");

        RefreshToken token1 = RefreshToken.builder()
                .user(user1)
                .tokenHash("same-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(token1);

        RefreshToken token2 = RefreshToken.builder()
                .user(user2)
                .tokenHash("same-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        assertThrows(Exception.class, () -> refreshTokenRepository.saveAndFlush(token2));
    }

    @Test
    void shouldDeleteExpiredAndRevokedTokens() {
        User user = createTestUser("rt3@test.com");

        RefreshToken expired = RefreshToken.builder()
                .user(user)
                .tokenHash("expired-hash")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revoked(true)
                .build();
        RefreshToken valid = RefreshToken.builder()
                .user(user)
                .tokenHash("valid-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.saveAll(List.of(expired, valid));
        refreshTokenRepository.flush();

        int deleted = refreshTokenRepository.deleteExpiredAndRevokedBefore(LocalDateTime.now());
        assertEquals(1, deleted);
        assertEquals(1, refreshTokenRepository.findAll().size());
    }

    @Test
    void shouldRevokeAllActiveTokensForUser() {
        User user = createTestUser("rt4@test.com");

        RefreshToken active1 = RefreshToken.builder()
                .user(user)
                .tokenHash("active1-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        RefreshToken active2 = RefreshToken.builder()
                .user(user)
                .tokenHash("active2-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        RefreshToken revoked = RefreshToken.builder()
                .user(user)
                .tokenHash("revoked-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(true)
                .build();

        refreshTokenRepository.saveAll(List.of(active1, active2, revoked));
        refreshTokenRepository.flush();

        refreshTokenRepository.revokeAllActiveTokensForUser(user);

        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(user.getId());
        assertEquals(0, active.size());
    }
}
