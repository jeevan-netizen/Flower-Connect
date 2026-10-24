package com.flowerconnect.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.flowerconnect.config.TestClockConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fast check that {@link AppProperties} binds {@code app.webhook-secret} from
 * {@code application-test.yml} without needing a database (no Testcontainers).
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class AppPropertiesBindingTest {

    @DynamicPropertySource
    static void noSchemaValidation(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private AppProperties appProperties;

    @Test
    void webhookSecretIsBoundFromTestYaml() {
        assertThat(appProperties.getWebhookSecret())
            .isEqualTo("test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars");
    }
}
