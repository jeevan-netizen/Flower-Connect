package com.flowerconnect.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the defaults for {@link CheckoutProperties} (decision B-2, D-5).
 */
class CheckoutPropertiesTest {

    @Test
    void shouldDefaultPlatformFeeToZero() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(BigDecimal.ZERO, props.getPlatformFee());
    }

    @Test
    void shouldDefaultCommissionRateToTenPercent() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(new BigDecimal("0.10"), props.getDefaultCommissionRate());
    }

    @Test
    void shouldDefaultMinLeadTimeMinutesTo120() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(120, props.getMinLeadTimeMinutes());
    }

    @Test
    void shouldDefaultMaxBookingHorizonDaysTo7() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(7, props.getMaxBookingHorizonDays());
    }

    @Test
    void shouldAllowPlatformFeeOverride() {
        CheckoutProperties props = new CheckoutProperties();
        props.setPlatformFee(new BigDecimal("5.00"));
        assertEquals(new BigDecimal("5.00"), props.getPlatformFee());
    }

    @Test
    void shouldHaveDefaultQuoteSigningSecret() {
        CheckoutProperties props = new CheckoutProperties();
        assertNotNull(props.getQuoteSigningSecret());
        assertTrue(props.getQuoteSigningSecret().length() >= 32);
    }

    @Test
    void shouldAllowQuoteSigningSecretOverride() {
        CheckoutProperties props = new CheckoutProperties();
        props.setQuoteSigningSecret("custom-secret-key-for-signing-at-least-32-chars");
        assertEquals("custom-secret-key-for-signing-at-least-32-chars", props.getQuoteSigningSecret());
    }

    @Test
    void shouldRejectSecretShorterThan32Characters() {
        CheckoutProperties props = new CheckoutProperties();
        props.setQuoteSigningSecret("too-short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    void shouldRejectNullSecret() {
        CheckoutProperties props = new CheckoutProperties();
        props.setQuoteSigningSecret(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("at least 32 characters"));
    }

    @Test
    void shouldRejectInsecureDefaultSecretInProductionProfile() {
        CheckoutProperties props = new CheckoutProperties();
        org.springframework.core.env.Environment prodEnv = org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
        org.mockito.Mockito.when(prodEnv.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> props.validate(prodEnv));
        assertTrue(ex.getMessage().contains("Default quote signing secret cannot be used in production profile"));
    }

    @Test
    void shouldAcceptInsecureDefaultSecretInNonProductionProfile() {
        CheckoutProperties props = new CheckoutProperties();
        org.springframework.core.env.Environment devEnv = org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
        org.mockito.Mockito.when(devEnv.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(false);

        assertDoesNotThrow(() -> props.validate(devEnv));
    }

    @Test
    void shouldAcceptValidSecretInProductionProfile() {
        CheckoutProperties props = new CheckoutProperties();
        props.setQuoteSigningSecret("production-secret-with-at-least-32-characters-length");
        org.springframework.core.env.Environment prodEnv = org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
        org.mockito.Mockito.when(prodEnv.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))).thenReturn(true);

        assertDoesNotThrow(() -> props.validate(prodEnv));
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.boot.context.properties.EnableConfigurationProperties(CheckoutProperties.class)
    static class TestPropertiesConfig {
    }

    @Test
    void productionConfigurationFailsToStartWithoutSecret() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=prod")
                .withUserConfiguration(TestPropertiesConfig.class)
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure().getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Default quote signing secret cannot be used in production profile");
                });
    }

    @Test
    void productionConfigurationFailsWithTooShortSecret() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.checkout.quote-signing-secret=too-short-secret"
                )
                .withUserConfiguration(TestPropertiesConfig.class)
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure().getCause())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("at least 32 characters");
                });
    }

    @Test
    void productionConfigurationStartsSuccessfullyWithValidSecret() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "app.checkout.quote-signing-secret=production-secret-with-at-least-32-characters-length"
                )
                .withUserConfiguration(TestPropertiesConfig.class)
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasNotFailed();
                    CheckoutProperties bean = context.getBean(CheckoutProperties.class);
                    org.assertj.core.api.Assertions.assertThat(bean.getQuoteSigningSecret())
                            .isEqualTo("production-secret-with-at-least-32-characters-length");
                });
    }
}
