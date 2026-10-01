package com.flowerconnect.exception;

public class JwtAuthenticationException extends BusinessException {

    public JwtAuthenticationException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHORIZED, message, cause);
    }
}
