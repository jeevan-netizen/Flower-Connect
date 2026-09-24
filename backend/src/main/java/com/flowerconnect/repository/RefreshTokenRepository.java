package com.flowerconnect.repository;

import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user u JOIN FETCH u.role WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user u JOIN FETCH u.role WHERE rt.tokenHash = :tokenHash AND rt.revokedAt IS NOT NULL")
    Optional<RefreshToken> findRevokedByTokenHash(String tokenHash);

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user u JOIN FETCH u.role WHERE rt.id = :id")
    Optional<RefreshToken> findByIdWithUser(Long id);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.user = :user AND rt.revokedAt IS NULL")
    void revokeAllActiveTokensForUser(User user);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = CURRENT_TIMESTAMP WHERE rt.familyId = :familyId AND rt.revokedAt IS NULL")
    int revokeAllTokensInFamily(String familyId);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR (rt.revokedAt IS NOT NULL AND rt.expiresAt < :now)")
    int deleteExpiredAndRevokedBefore(LocalDateTime now);
}
