package com.flowerconnect.repository;

import com.flowerconnect.domain.PasswordResetToken;
import com.flowerconnect.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Lookup by token_hash only (the raw token is never exposed). Token expiry is
 * checked by the service layer against the injected {@link java.time.Clock}.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    Optional<PasswordResetToken> findByTokenHashAndUsedAtIsNull(String tokenHash);

    List<PasswordResetToken> findByUser(User user);

    List<PasswordResetToken> findByUserId(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.usedAt = :now WHERE p.tokenHash = :tokenHash AND p.usedAt IS NULL")
    int markUsedByTokenHash(String tokenHash, LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :now")
    int deleteExpiredBefore(LocalDateTime now);
}