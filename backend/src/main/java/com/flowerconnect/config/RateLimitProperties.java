package com.flowerconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private AuthEndpoints auth = new AuthEndpoints();

    public AuthEndpoints getAuth() {
        return auth;
    }

    public void setAuth(AuthEndpoints auth) {
        this.auth = auth;
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