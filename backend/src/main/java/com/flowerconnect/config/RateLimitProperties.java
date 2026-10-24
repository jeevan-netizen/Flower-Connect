package com.flowerconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private AuthEndpoints auth = new AuthEndpoints();
    /**
     * Requests per hour allowed for the public payment webhook, keyed by
     * client IP (the webhook carries no email to key on).
     */
    private int webhookRequestsPerHour = 100;

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
}