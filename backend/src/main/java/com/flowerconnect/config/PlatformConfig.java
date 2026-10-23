package com.flowerconnect.config;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Platform configuration key/value store (plan section 9, `platform_config`).
 *
 * <p>The table is intentionally generic: a single `config_key` / `config_value`
 * pair per row, so Phase 10 can add commission, platform-fee and timeout rows
 -- without another migration. Phase 6 uses only the `auto_reject_minutes` row,
-- which the auto-reject job (task 6.5) reads to decide whether an unaccepted
-- PLACED order is stale enough to cancel.
 *
 * <p>Values are stored as text so the same table can hold numbers, durations
-- and flags. Callers parse the value they need; a non-parseable value fails
-- fast at service startup rather than silently disabling a feature.
 */
@Entity
@Table(name = "platform_config")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PlatformConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", length = 64, nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", length = 255, nullable = false)
    private String configValue;

    @Column(name = "description", length = 512)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
