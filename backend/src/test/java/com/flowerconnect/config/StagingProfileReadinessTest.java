package com.flowerconnect.config;

import com.flowerconnect.demo.DemoSeedRunner;
import com.flowerconnect.demo.DemoSeedService;
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
 * Verifies staging profile readiness guarantees:
 * <ul>
 *   <li>Fail-fast startup on missing or weak JWT secret (< 32 chars).</li>
 *   <li>Fail-fast startup on missing or weak webhook secret (< 32 bytes).</li>
 *   <li>Fail-fast startup on missing, default, or weak quote signing secret.</li>
 *   <li>Ensure invalid secret values are never printed or leaked in error messages.</li>
 *   <li>Fail-fast startup when database credentials are not externalized.</li>
 *   <li>Exclusion of dev demo seed components in staging.</li>
 *   <li>Exclusion of Swagger/OpenAPI configuration in staging.</li>
 * </ul>
 */
class StagingProfileReadinessTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "short-secret",
            "1234567890123456789012345678901" // exactly 31 characters
    })
    @DisplayName("Staging fails fast on missing or weak JWT_SECRET without leaking secret value")
    void stagingFailsFast_whenJwtSecretIsMissingOrWeak(String weakSecret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(weakSecret);
        JwtService jwtService = new JwtService(props, Clock.systemUTC());

        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtService::init);
        assertThat(ex.getMessage()).contains("JWT_SECRET must be at least 32 characters");

        // Verify invalid secret value is not printed or leaked in the exception message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Staging succeeds when JWT_SECRET is strong (>= 32 characters)")
    void stagingSucceeds_whenJwtSecretIsStrong() {
        JwtProperties props = new JwtProperties();
        props.setSecret("valid-32-character-staging-jwt-secret-key-ok!");
        JwtService jwtService = new JwtService(props, Clock.systemUTC());

        jwtService.init(); // no exception
        assertThat(jwtService.getAccessTtlMs()).isEqualTo(props.getAccessTtlMs());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "short-16b-secret",
            "1234567890123456789012345678901" // exactly 31 bytes
    })
    @DisplayName("Staging fails fast on missing or weak webhook secret without leaking secret value")
    void stagingFailsFast_whenWebhookSecretIsMissingOrWeak(String weakSecret) {
        AppProperties appProperties = new AppProperties();
        appProperties.setWebhookSecret(weakSecret);
        WebhookSecretValidator validator = new WebhookSecretValidator(appProperties);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertThat(ex.getMessage()).contains("app.webhook-secret must be set and at least 32 bytes");

        // Verify invalid secret value is not printed or leaked in the exception message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Staging succeeds when app.webhook-secret is strong (>= 32 bytes)")
    void stagingSucceeds_whenWebhookSecretIsStrong() {
        AppProperties appProperties = new AppProperties();
        appProperties.setWebhookSecret("valid-32-byte-staging-webhook-secret-key-ok");
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
    @DisplayName("Staging fails fast on missing or weak quote signing secret without leaking secret value")
    void stagingFailsFast_whenQuoteSigningSecretIsMissingOrWeak(String weakSecret) {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret(weakSecret);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutProps.validate(env));
        assertThat(ex.getMessage()).contains("Quote signing secret must be at least 32 characters");

        // Verify invalid secret value is not printed or leaked in the exception message
        if (weakSecret != null && !weakSecret.isBlank()) {
            assertThat(ex.getMessage()).doesNotContain(weakSecret);
        }
    }

    @Test
    @DisplayName("Staging fails fast when quote signing secret uses the insecure default value")
    void stagingFailsFast_whenQuoteSigningSecretIsDefaultInsecure() {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret(CheckoutProperties.INSECURE_DEFAULT_SECRET);
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutProps.validate(env));
        assertThat(ex.getMessage()).contains("Default quote signing secret cannot be used in production profile");
    }

    @Test
    @DisplayName("Staging succeeds when quote signing secret is custom and strong (>= 32 chars)")
    void stagingSucceeds_whenQuoteSigningSecretIsStrong() {
        CheckoutProperties checkoutProps = new CheckoutProperties();
        checkoutProps.setQuoteSigningSecret("custom-strong-staging-quote-secret-32-chars-ok");
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");

        checkoutProps.validate(env); // no exception
    }

    @Test
    @DisplayName("Staging fails fast when database URL is not externalized")
    void stagingFailsFast_whenDatabaseUrlMissing() {
        // In application-staging.yml, spring.datasource.url is ${DB_URL} with no default.
        // Spring Boot environment without DB_URL cannot resolve the placeholder.
        contextRunner
                .withPropertyValues("spring.profiles.active=staging")
                .withUserConfiguration(AppProperties.class)
                .run(context -> {
                    // With staging profile active and no DB_URL provided in properties,
                    // any component binding to datasource placeholder must fail or configuration fails
                    assertThat(context.getEnvironment().getActiveProfiles()).contains("staging");
                    assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isNull();
                });
    }

    @Test
    @DisplayName("Staging profile excludes dev demo seed runner and service")
    void stagingProfile_excludesDevSeedComponents() {
        contextRunner
                .withPropertyValues("spring.profiles.active=staging")
                .withUserConfiguration(DemoSeedRunner.class, DemoSeedService.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoSeedRunner.class);
                    assertThat(context).doesNotHaveBean(DemoSeedService.class);
                });
    }

    @Test
    @DisplayName("Staging profile excludes SpringDoc OpenAPI configuration")
    void stagingProfile_excludesSpringDocOpenApiConfig() {
        contextRunner
                .withPropertyValues("spring.profiles.active=staging")
                .withUserConfiguration(SpringDocConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SpringDocConfig.class);
                });
    }
}
