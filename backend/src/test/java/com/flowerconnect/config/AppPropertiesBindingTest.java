package com.flowerconnect.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast check that {@link AppProperties} binds {@code app.webhook-secret}
 * without needing a database (no Testcontainers).
 *
 * A minimal Spring context loads only {@link AppProperties} via
 * {@link EnableConfigurationProperties}, so no DataSource auto-configuration
 * runs and no database connection is required.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AppProperties.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.webhook-secret=test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars")
class AppPropertiesBindingTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void webhookSecretIsBound() {
        assertThat(appProperties.getWebhookSecret())
            .isEqualTo("test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars");
    }
}
