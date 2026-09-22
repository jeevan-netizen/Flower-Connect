package com.flowerconnect.exception;

public class TokenRefreshException extends BusinessException {

    public TokenRefreshException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public TokenRefreshException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHORIZED, message, cause);
    }
}
