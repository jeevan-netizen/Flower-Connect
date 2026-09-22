package com.flowerconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private long accessTtlMs = 900000L;
    private long refreshTtlMs = 604800000L;
    private long refreshGraceSeconds = 10L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtlMs() {
        return accessTtlMs;
    }

    public void setAccessTtlMs(long accessTtlMs) {
        this.accessTtlMs = accessTtlMs;
    }

    public long getRefreshTtlMs() {
        return refreshTtlMs;
    }

    public void setRefreshTtlMs(long refreshTtlMs) {
        this.refreshTtlMs = refreshTtlMs;
    }

    public long getRefreshGraceSeconds() {
        return refreshGraceSeconds;
    }

    public void setRefreshGraceSeconds(long refreshGraceSeconds) {
        this.refreshGraceSeconds = refreshGraceSeconds;
    }
}
