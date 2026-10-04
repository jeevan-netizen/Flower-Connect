package com.flowerconnect.storage;

/**
 * Raised when a storage backend cannot complete an operation.
 *
 * <p>Kept distinct from {@link com.flowerconnect.exception.BusinessException} so
 * a caller can tell an infrastructure failure (retryable later, HTTP 500) from
 * a rejected request (the caller's fault, HTTP 4xx) without inspecting a
 * message string.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}