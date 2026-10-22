package com.flowerconnect.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence for {@link PlatformConfig} rows (plan task 6.5).
 *
 * <p>The auto-reject service reads `auto_reject_minutes` through
 * {@link #findByConfigKey(String)}. Phase 10 may add more keys without
-- changing this interface.
 */
@Repository
public interface PlatformConfigRepository extends JpaRepository<PlatformConfig, Long> {

    Optional<PlatformConfig> findByConfigKey(String configKey);
}
