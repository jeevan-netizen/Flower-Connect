package com.flowerconnect.payment.client;

/**
 * Thrown when the Razorpay API call fails (network error, invalid credentials,
 * non-2xx response, or unparseable body).
 */
public class RazorpayClientException extends RuntimeException {

    public RazorpayClientException(String message) {
        super(message);
    }

    public RazorpayClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
