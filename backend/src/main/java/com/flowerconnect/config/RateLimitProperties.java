package com.flowerconnect.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /**
     * Whether the rate-limit filter is active. Off by default so the feature is
     * opt-in per profile; switched on for dev and prod in application.yml /
     * application-prod.yml.
     */
    private boolean enabled = false;

    /**
     * Proxies whose forwarded headers (X-Forwarded-For, X-Real-IP) are trusted
     * when resolving the client IP. Empty by default, so forwarded headers are
     * never honored unless explicitly configured -- a client cannot spoof its IP
     * by sending a forged header. Operators add the reverse-proxy IP/CIDR here.
     */
    private List<String> trustedProxies = List.of();

    private AuthEndpoints auth = new AuthEndpoints();

    /**
     * Requests per hour allowed for the public payment webhook, keyed by client
     * IP (the webhook carries no email to key on).
     */
    private int webhookRequestsPerHour = 100;

    /**
     * Limits for sensitive mutating endpoints that require an authenticated
     * caller. Each is keyed by the authenticated principal (email), with a
     * client-IP fallback for unauthenticated calls. Windows are one hour.
     */
    private SensitiveEndpoints sensitive = new SensitiveEndpoints();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(List<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    public AuthEndpoints getAuth() {
        return auth;
    }

    public void setAuth(AuthEndpoints auth) {
        this.auth = auth;
    }

    public int getWebhookRequestsPerHour() {
        return webhookRequestsPerHour;
    }

    public void setWebhookRequestsPerHour(int webhookRequestsPerHour) {
        this.webhookRequestsPerHour = webhookRequestsPerHour;
    }

    public SensitiveEndpoints getSensitive() {
        return sensitive;
    }

    public void setSensitive(SensitiveEndpoints sensitive) {
        this.sensitive = sensitive;
    }

    public static class AuthEndpoints {
        private int loginRequestsPerHour = 5;
        private int registerRequestsPerHour = 5;
        private int forgotPasswordRequestsPerHour = 5;
        private int resetPasswordRequestsPerHour = 5;

        public int getLoginRequestsPerHour() {
            return loginRequestsPerHour;
        }

        public void setLoginRequestsPerHour(int loginRequestsPerHour) {
            this.loginRequestsPerHour = loginRequestsPerHour;
        }

        public int getRegisterRequestsPerHour() {
            return registerRequestsPerHour;
        }

        public void setRegisterRequestsPerHour(int registerRequestsPerHour) {
            this.registerRequestsPerHour = registerRequestsPerHour;
        }

        public int getForgotPasswordRequestsPerHour() {
            return forgotPasswordRequestsPerHour;
        }

        public void setForgotPasswordRequestsPerHour(int forgotPasswordRequestsPerHour) {
            this.forgotPasswordRequestsPerHour = forgotPasswordRequestsPerHour;
        }

        public int getResetPasswordRequestsPerHour() {
            return resetPasswordRequestsPerHour;
        }

        public void setResetPasswordRequestsPerHour(int resetPasswordRequestsPerHour) {
            this.resetPasswordRequestsPerHour = resetPasswordRequestsPerHour;
        }
    }

    public static class SensitiveEndpoints {
        private int checkoutRequestsPerHour = 30;
        private int checkoutQuoteRequestsPerHour = 60;
        private int disputeRequestsPerHour = 10;
        private int reviewRequestsPerHour = 20;
        private int addressRequestsPerHour = 20;
        private int cartMutationRequestsPerHour = 60;
        private int uploadRequestsPerHour = 30;
        private int posSaleRequestsPerHour = 300;

        public int getCheckoutRequestsPerHour() {
            return checkoutRequestsPerHour;
        }

        public void setCheckoutRequestsPerHour(int checkoutRequestsPerHour) {
            this.checkoutRequestsPerHour = checkoutRequestsPerHour;
        }

        public int getCheckoutQuoteRequestsPerHour() {
            return checkoutQuoteRequestsPerHour;
        }

        public void setCheckoutQuoteRequestsPerHour(int checkoutQuoteRequestsPerHour) {
            this.checkoutQuoteRequestsPerHour = checkoutQuoteRequestsPerHour;
        }

        public int getDisputeRequestsPerHour() {
            return disputeRequestsPerHour;
        }

        public void setDisputeRequestsPerHour(int disputeRequestsPerHour) {
            this.disputeRequestsPerHour = disputeRequestsPerHour;
        }

        public int getReviewRequestsPerHour() {
            return reviewRequestsPerHour;
        }

        public void setReviewRequestsPerHour(int reviewRequestsPerHour) {
            this.reviewRequestsPerHour = reviewRequestsPerHour;
        }

        public int getAddressRequestsPerHour() {
            return addressRequestsPerHour;
        }

        public void setAddressRequestsPerHour(int addressRequestsPerHour) {
            this.addressRequestsPerHour = addressRequestsPerHour;
        }

        public int getCartMutationRequestsPerHour() {
            return cartMutationRequestsPerHour;
        }

        public void setCartMutationRequestsPerHour(int cartMutationRequestsPerHour) {
            this.cartMutationRequestsPerHour = cartMutationRequestsPerHour;
        }

        public int getUploadRequestsPerHour() {
            return uploadRequestsPerHour;
        }

        public void setUploadRequestsPerHour(int uploadRequestsPerHour) {
            this.uploadRequestsPerHour = uploadRequestsPerHour;
        }

        public int getPosSaleRequestsPerHour() {
            return posSaleRequestsPerHour;
        }

        public void setPosSaleRequestsPerHour(int posSaleRequestsPerHour) {
            this.posSaleRequestsPerHour = posSaleRequestsPerHour;
        }
    }
}
