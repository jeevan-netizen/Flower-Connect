package com.flowerconnect.exception;

public class ResourceConflictException extends BusinessException {

    public ResourceConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
