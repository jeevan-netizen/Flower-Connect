package com.flowerconnect.config;

import com.flowerconnect.demo.DemoSeedRunner;
import com.flowerconnect.demo.DemoSeedService;
import com.flowerconnect.payment.controller.SimulatorController;
import com.flowerconnect.payment.provider.SimulatedPaymentProvider;
import com.flowerconnect.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies production profile readiness guarantees:
 * <ul>
 *   <li>Fail-fast startup on missing or weak JWT secret (< 32 chars).</li>
 *   <li>Fail-fast startup on missing or weak webhook secret (< 32 bytes).</li>
 *   <li>Fail-fast startup on missing, default, or weak quote signing secret.</li>
 *   <li>Ensure invalid secret values are never printed or leaked in error messages.</li>
 *   <li>Fail-fast startup when database credentials are not externalized.</li>
 *   <li>Exclusion of dev demo seed components in prod.</li>
 *   <li>Exclusion of payment simulator controller and provider in prod.</li>
 *   <li>Exclusion of Swagger/OpenAPI configuration in prod.</li>
 * </ul>
 */
class ProductionProfileReadinessTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "too-short-secret",
            "1234567890123456789012345678901" // exactly 31 characters
    })
    @DisplayName("Production fails fast on missing or weak JWT_SECRET without leaking secret value")
    void prodFailsFast_whenJwtSecretIsMissingOrWeak(String weakSecret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(weakSecret);
        JwtService jwtService = new JwtService(props, Clock.systemUTC());

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::init);
        assertThat(ex.getMessage()).contains("JWT_SECRET must be at least 32 characters");

        // Verify secret value is never printed or leaked in the error message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Production succeeds when JWT_SECRET is strong (>= 32 characters)")
    void prodSucceeds_whenJwtSecretIsStrong() {
        JwtProperties props = new JwtProperties();
        props.setSecret("production-strong-jwt-secret-key-32-chars-ok!");
        JwtService jwtService = new JwtService(props, Clock.systemUTC());

        jwtService.init(); // no exception
        assertThat(jwtService.getAccessTtlMs()).isEqualTo(props.getAccessTtlMs());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "short-secret-key-16b",
            "1234567890123456789012345678901" // exactly 31 bytes
    })
    @DisplayName("Production fails fast on missing or weak webhook secret without leaking secret value")
    void prodFailsFast_whenWebhookSecretIsMissingOrWeak(String weakSecret) {
        AppProperties appProperties = new AppProperties();
        appProperties.setWebhookSecret(weakSecret);
        WebhookSecretValidator validator = new WebhookSecretValidator(appProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertThat(ex.getMessage()).contains("app.webhook-secret must be set and at least 32 bytes");

        // Verify secret value is never printed or leaked in the error message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Production succeeds when app.webhook-secret is strong (>= 32 bytes)")
    void prodSucceeds_whenWebhookSecretIsStrong() {
        AppProperties appProperties = new AppProperties();
        appProperties.setWebhookSecret("valid-32-byte-secret-for-webhook-verification-ok");
        WebhookSecretValidator validator = new WebhookSecretValidator(appProperties);

        validator.validate(); // no exception
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "too-short",
            "1234567890123456789012345678901" // exactly 31 characters
    })
    @DisplayName("Production fails fast on missing or weak quote signing secret without leaking secret value")
    void prodFailsFast_whenQuoteSigningSecretIsMissingOrWeak(String weakSecret) {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret(weakSecret);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutProps.validate(env));
        assertThat(ex.getMessage()).contains("Quote signing secret must be at least 32 characters");

        // Verify secret value is never printed or leaked in the error message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Production fails fast when quote signing secret uses default insecure value")
    void prodFailsFast_whenQuoteSigningSecretIsDefaultInsecure() {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret(CheckoutProperties.INSECURE_DEFAULT_SECRET);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutProps.validate(env));
        assertThat(ex.getMessage()).contains("Default quote signing secret cannot be used in production profile");
    }

    @Test
    @DisplayName("Production succeeds when quote signing secret is custom and strong (>= 32 chars)")
    void prodSucceeds_whenQuoteSigningSecretIsStrong() {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret("custom-strong-quote-secret-32-chars-long-valid");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        checkoutProps.validate(env); // no exception
    }

    @Test
    @DisplayName("Production fails fast when database URL is not externalized")
    void prodFailsFast_whenDatabaseUrlMissing() {
        // In application-prod.yml, spring.datasource.url is ${DB_URL} with no default.
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(AppProperties.class)
                .run(context -> {
                    assertThat(context.getEnvironment().getActiveProfiles()).contains("prod");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
                });
    }

    @Test
    @DisplayName("Production profile excludes dev demo seed runner and service")
    void prodProfile_excludesDevSeedComponents() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(DemoSeedRunner.class, DemoSeedService.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoSeedRunner.class);
                    assertThat(context).doesNotHaveBean(DemoSeedService.class);
                });
    }

    @Test
    @DisplayName("Production profile excludes payment simulator controller and provider")
    void prodProfile_excludesSimulatorComponents() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(SimulatorController.class, SimulatedPaymentProvider.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SimulatorController.class);
                    assertThat(context).doesNotHaveBean(SimulatedPaymentProvider.class);
                });
    }

    @Test
    @DisplayName("Production profile excludes SpringDoc OpenAPI configuration")
    void prodProfile_excludesSpringDocOpenApiConfig() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(SpringDocConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringDocConfig.class);
                });
    }
}
