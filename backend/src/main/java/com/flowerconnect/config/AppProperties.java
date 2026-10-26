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
    private String autoRejectCron = "0 0 * * * ?";
    private int autoRejectBatchSize = 100;
    private String paymentExpiryCron = "0 * * * * ?";
    private int paymentExpiryBatchSize = 100;
    private int paymentExpiryMinutes = 30;

    /**
     * Cron expression for the weekly settlement job (plan Phase 10, task 10.4).
     * Defaults to Sunday midnight UTC ("0 0 0 * * 0"). Override with
     * {@code APP_SETTLEMENT_CRON} or {@code app.settlement-cron}. Set to {@code -}
     * (Spring's {@code CRON_DISABLED}) to turn the job off.
     */
    private String settlementCron = "0 0 0 * * 0";

    /**
     * Threshold in days before unattached negative dues are considered overdue (plan task 10.7).
     * Defaults to 30 days. Override with {@code APP_BILLING_OVERDUE_DAYS} or {@code app.overdue-days}.
     */
    private int overdueDays = 30;


    /**
     * Cron expression for the notification SSE heartbeat (plan task 9.5). The
     * heartbeat keeps live event streams open and sweeps out stale/disconnected
     * emitters. Override with {@code APP_NOTIFICATION_SSE_HEARTBEAT_CRON} or
     * {@code app.notification.sse-heartbeat-cron}. Set to {@code -} (Spring's
     * {@code CRON_DISABLED}) to turn the heartbeat off (used in the test profile).
     */
    private String sseHeartbeatCron = "0/30 * * * * *";

    /**
     * SSE emitter timeout (seconds). A connection with no successful send for
     * this long is considered stale and removed. Heartbeats keep live
     * connections well under this bound. Override with
     * {@code APP_NOTIFICATION_SSE_TIMEOUT_SECONDS} or
     * {@code app.notification.sse-timeout-seconds}.
     */
    private int sseTimeoutSeconds = 90;

    /**
     * Maximum number of concurrent SSE connections a single user may hold.
     * Connections beyond the cap are refused (the emitter is completed
     * immediately so the client can reconnect later). Override with
     * {@code APP_NOTIFICATION_SSE_MAX_CONNECTIONS_PER_USER} or
     * {@code app.notification.sse-max-connections-per-user}.
     */
    private int sseMaxConnectionsPerUser = 5;

    /**
     * Time-to-live (minutes) for a simulated online payment intent. After the
     * TTL elapses the intent expires and the pending order can no longer be
     * resumed through {@code POST /api/v1/payments/{orderId}/resume}.
     * Override with {@code APP_PAYMENT_INTENT_TTL_MINUTES} or
     * {@code app.payment-intent-ttl-minutes}.
     */
    private int intentTtlMinutes = 30;

    /**
     * Shared secret used to verify signatures on the public payment webhook
     * ({@code POST /api/v1/payments/webhook}). Configured from
     * {@code APP_WEBHOOK_SECRET} / {@code app.webhook-secret} only; there is no
     * default. In the {@code prod} profile the startup validator rejects a
     * missing or shorter-than-32-byte secret.
     */
    private String webhookSecret;

    /**
     * Tolerance window (seconds) for webhook timestamps. Defaults to five
     * minutes. Override with {@code APP_WEBHOOK_TOLERANCE_SECONDS} or
     * {@code app.webhook-tolerance-seconds}.
     */
    private int webhookToleranceSeconds = 300;

    /**
     * Maximum webhook request body size (bytes). Override with
     * {@code APP_WEBHOOK_MAX_BODY_BYTES} or {@code app.webhook-max-body-bytes}.
     */
    private int webhookMaxBodyBytes = 65_536;

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

    /**
     * Cron expression for the auto-reject job (plan task 6.5). Defaults to
     * hourly, so unaccepted PLACED orders are checked at least once per hour.
     * Override with {@code APP_AUTO_REJECT_CRON} or {@code app.auto-reject-cron}.
     * Set to {@code -} (Spring's {@code CRON_DISABLED}) to turn the job off.
     */
    public String getAutoRejectCron() {
        return autoRejectCron;
    }

    public void setAutoRejectCron(String autoRejectCron) {
        this.autoRejectCron = autoRejectCron;
    }

    /**
     * Maximum number of orders one auto-reject run examines. The run holds the
     * order row lock per candidate via the state machine, so an unbounded run
     -- over a large backlog would hold many locks. Overflow is left for the
     * next scheduled run. Override with {@code APP_AUTO_REJECT_BATCH_SIZE}.
     */
    public int getAutoRejectBatchSize() {
        return autoRejectBatchSize;
    }

    public void setAutoRejectBatchSize(int autoRejectBatchSize) {
        this.autoRejectBatchSize = autoRejectBatchSize;
    }

    /**
     * Cron expression for the payment-expiry job (plan task 7.5). Defaults to
     * every minute, so unpaid {@code PENDING_PAYMENT} orders are checked
     * frequently and reservations cannot leak for long. Override with
     * {@code APP_PAYMENT_EXPIRY_CRON} or {@code app.payment-expiry-cron}.
     * Set to {@code -} (Spring's {@code CRON_DISABLED}) to turn the job off.
     */
    public String getPaymentExpiryCron() {
        return paymentExpiryCron;
    }

    public void setPaymentExpiryCron(String paymentExpiryCron) {
        this.paymentExpiryCron = paymentExpiryCron;
    }

    /**
     * Maximum number of orders one payment-expiry run examines. The run holds
     * the order row lock per candidate via the state machine, so an unbounded
     * run over a large backlog would hold many locks. Overflow is left for the
     * next scheduled run. Override with {@code APP_PAYMENT_EXPIRY_BATCH_SIZE}.
     */
    public int getPaymentExpiryBatchSize() {
        return paymentExpiryBatchSize;
    }

    public void setPaymentExpiryBatchSize(int paymentExpiryBatchSize) {
        this.paymentExpiryBatchSize = paymentExpiryBatchSize;
    }

    /**
     * Payment-expiry threshold (minutes). A {@code PENDING_PAYMENT} order older
     * than this is cancelled and its reserved stock released. Override with
     * {@code APP_PAYMENT_EXPIRY_MINUTES} or {@code app.payment-expiry-minutes}.
     */
    public int getPaymentExpiryMinutes() {
        return paymentExpiryMinutes;
    }

    public void setPaymentExpiryMinutes(int paymentExpiryMinutes) {
        this.paymentExpiryMinutes = paymentExpiryMinutes;
    }

    public int getIntentTtlMinutes() {
        return intentTtlMinutes;
    }

    public void setIntentTtlMinutes(int intentTtlMinutes) {
        this.intentTtlMinutes = intentTtlMinutes;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public int getWebhookToleranceSeconds() {
        return webhookToleranceSeconds;
    }

    public void setWebhookToleranceSeconds(int webhookToleranceSeconds) {
        this.webhookToleranceSeconds = webhookToleranceSeconds;
    }

    public int getWebhookMaxBodyBytes() {
        return webhookMaxBodyBytes;
    }

    public void setWebhookMaxBodyBytes(int webhookMaxBodyBytes) {
        this.webhookMaxBodyBytes = webhookMaxBodyBytes;
    }

    public String getSseHeartbeatCron() {
        return sseHeartbeatCron;
    }

    public void setSseHeartbeatCron(String sseHeartbeatCron) {
        this.sseHeartbeatCron = sseHeartbeatCron;
    }

    public int getSseTimeoutSeconds() {
        return sseTimeoutSeconds;
    }

    public void setSseTimeoutSeconds(int sseTimeoutSeconds) {
        this.sseTimeoutSeconds = sseTimeoutSeconds;
    }

    public int getSseMaxConnectionsPerUser() {
        return sseMaxConnectionsPerUser;
    }

    public void setSseMaxConnectionsPerUser(int sseMaxConnectionsPerUser) {
        this.sseMaxConnectionsPerUser = sseMaxConnectionsPerUser;
    }

    public String getSettlementCron() {
        return settlementCron;
    }

    public void setSettlementCron(String settlementCron) {
        this.settlementCron = settlementCron;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(int overdueDays) {
        this.overdueDays = overdueDays;
    }
}
