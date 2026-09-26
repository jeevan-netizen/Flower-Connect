package com.flowerconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:5173";
    private boolean refreshCookieSecure = true;
    private int resetTokenTtlMinutes = 30;
    private String tokenCleanupCron = "0 0 2 * * ?";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }

    public int getResetTokenTtlMinutes() {
        return resetTokenTtlMinutes;
    }

    public void setResetTokenTtlMinutes(int resetTokenTtlMinutes) {
        this.resetTokenTtlMinutes = resetTokenTtlMinutes;
    }

    public String getTokenCleanupCron() {
        return tokenCleanupCron;
    }

    public void setTokenCleanupCron(String tokenCleanupCron) {
        this.tokenCleanupCron = tokenCleanupCron;
    }
}