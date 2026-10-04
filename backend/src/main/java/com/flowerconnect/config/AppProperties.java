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
    private String expirySweepCron = "0 0 3 * * ?";
    private int expirySweepMaxRows = 200;

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

    /**
     * Cron expression for the inventory expiry sweep (plan task 3.7). Defaults to 03:00
     * daily, after the token cleanup and outside any order-processing window. Override
     * with {@code APP_EXPIRY_SWEEP_CRON} or {@code app.expiry-sweep-cron}.
     */
    public String getExpirySweepCron() {
        return expirySweepCron;
    }

    public void setExpirySweepCron(String expirySweepCron) {
        this.expirySweepCron = expirySweepCron;
    }

    /**
     * Maximum number of inventory rows one sweep examines. The sweep is deliberately
     * bounded: it holds a pessimistic write lock on every row it processes, so an
     * unbounded run over a large backlog would hold many locks for as long as the work
     * took. Overflow is left for the next scheduled run, which is safe because the
     * candidate predicate is self-clearing (see {@code InventoryExpiryService}).
     * Override with {@code APP_EXPIRY_SWEEP_MAX_ROWS}.
     */
    public int getExpirySweepMaxRows() {
        return expirySweepMaxRows;
    }

    public void setExpirySweepMaxRows(int expirySweepMaxRows) {
        this.expirySweepMaxRows = expirySweepMaxRows;
    }
}
