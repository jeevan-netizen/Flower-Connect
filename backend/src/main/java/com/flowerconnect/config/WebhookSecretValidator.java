package com.flowerconnect.config;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Startup validation of the webhook secret in {@code prod} and {@code staging} profiles.
 *
 * <p>The shared secret used to sign and verify payment webhooks must be
 * configured from environment configuration only and must be at least 32
 * bytes. Absence or a short secret fails startup so a deployment
 * can never accept unsigned webhook events with a weak or missing secret.
 * {@code .env.example} may contain a placeholder only.
 */
@Component
@Profile({"prod", "staging"})
public class WebhookSecretValidator {

    private final AppProperties appProperties;

    @Autowired
    public WebhookSecretValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void validate() {
        String secret = appProperties.getWebhookSecret();
        if (secret == null
                || secret.trim().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.webhook-secret must be set and at least 32 bytes in prod and staging profiles");
        }
    }
}
