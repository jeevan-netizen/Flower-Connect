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
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(Long userId);

    @Transactional
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user AND rt.revoked = false")
    void revokeAllActiveTokensForUser(User user);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR (rt.revoked = true AND rt.expiresAt < :now)")
    int deleteExpiredAndRevokedBefore(LocalDateTime now);
}
