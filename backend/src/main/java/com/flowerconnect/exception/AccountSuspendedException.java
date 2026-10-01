package com.flowerconnect.exception;

public class AccountSuspendedException extends BusinessException {

    public AccountSuspendedException(String message) {
        super(ErrorCode.ACCOUNT_SUSPENDED, message);
    }

    public AccountSuspendedException(String message, Throwable cause) {
        super(ErrorCode.ACCOUNT_SUSPENDED, message, cause);
    }
}
