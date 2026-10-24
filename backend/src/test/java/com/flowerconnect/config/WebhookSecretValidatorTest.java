package com.flowerconnect.config;

import com.flowerconnect.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link WebhookSecretValidator}: the prod profile fails startup
 * when the webhook secret is absent or shorter than 32 bytes.
 */
class WebhookSecretValidatorTest {

    @Test
    void missingSecretFailsStartup() {
        AppProperties app = new AppProperties();
        app.setWebhookSecret(null);
        assertThatThrownBy(() -> new WebhookSecretValidator(app).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortSecretFailsStartup() {
        AppProperties app = new AppProperties();
        app.setWebhookSecret("too-short");
        assertThatThrownBy(() -> new WebhookSecretValidator(app).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exactly32ByteSecretPasses() {
        AppProperties app = new AppProperties();
        app.setWebhookSecret("0123456789abcdef0123456789abcdef01");
        assertThatCode(() -> new WebhookSecretValidator(app).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void longerSecretPasses() {
        AppProperties app = new AppProperties();
        app.setWebhookSecret("a-very-long-webhook-secret-that-is-definitely-longer-than-32-bytes");
        assertThatCode(() -> new WebhookSecretValidator(app).validate())
                .doesNotThrowAnyException();
    }
}
