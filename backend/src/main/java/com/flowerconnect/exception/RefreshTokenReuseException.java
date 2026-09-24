package com.flowerconnect.exception;

public class RefreshTokenReuseException extends TokenRefreshException {

    public RefreshTokenReuseException(String message) {
        super(message);
    }

    public RefreshTokenReuseException(String message, Throwable cause) {
        super(message, cause);
    }
}
