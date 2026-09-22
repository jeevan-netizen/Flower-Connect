package com.flowerconnect.repository;

import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User createTestUser(String email) {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniquePhone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .phone(uniquePhone)
                .role(role)
                .status(User.Status.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Test
    void shouldSaveAndFindByTokenHash() {
        String uniqueEmail = "rt-" + UUID.randomUUID() + "@test.com";
        User user = createTestUser(uniqueEmail);
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("sha256-hash-value")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.save(token);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHash("sha256-hash-value");

        assertTrue(found.isPresent());
        assertEquals(token.getId(), found.get().getId());
    }

    @Test
    void shouldEnforceUniqueTokenHash() {
        String uniqueEmail1 = "rt1-" + UUID.randomUUID() + "@test.com";
        String uniqueEmail2 = "rt2-" + UUID.randomUUID() + "@test.com";
        User user1 = createTestUser(uniqueEmail1);
        User user2 = createTestUser(uniqueEmail2);

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
        String uniqueEmail = "rt3-" + UUID.randomUUID() + "@test.com";
        User user = createTestUser(uniqueEmail);

        RefreshToken expired = RefreshToken.builder()
                .user(user)
                .tokenHash("expired-hash")
                .expiresAt(LocalDateTime.now().minusDays(1))
                .revokedAt(LocalDateTime.now())
                .build();
        RefreshToken valid = RefreshToken.builder()
                .user(user)
                .tokenHash("valid-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        refreshTokenRepository.saveAll(List.of(expired, valid));
        refreshTokenRepository.flush();

        int deleted = refreshTokenRepository.deleteExpiredAndRevokedBefore(LocalDateTime.now());
        assertEquals(1, deleted);
        assertEquals(1, refreshTokenRepository.findAll().size());
    }

    @Test
    void shouldRevokeAllActiveTokensForUser() {
        String uniqueEmail = "rt4-" + UUID.randomUUID() + "@test.com";
        User user = createTestUser(uniqueEmail);

        RefreshToken active1 = RefreshToken.builder()
                .user(user)
                .tokenHash("active1-hash")
                .expiresAt(LocalDateTime.now().plusDays(7))
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
                .revokedAt(LocalDateTime.now())
                .build();

        refreshTokenRepository.saveAll(List.of(active1, active2, revoked));
        refreshTokenRepository.flush();

        refreshTokenRepository.revokeAllActiveTokensForUser(user);

        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(user.getId());
        assertEquals(0, active.size());
    }
}
