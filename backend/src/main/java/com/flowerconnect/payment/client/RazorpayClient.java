package com.flowerconnect.payment.client;

import java.math.BigDecimal;

/**
 * Client for the Razorpay Orders API (test mode).
 *
 * <p>Used server-side only to create a Razorpay Order for an ONLINE checkout.
 * The Key Secret is never exposed to the frontend. The interface is a Spring
 * bean so it can be mocked in tests without hitting the Razorpay API.
 */
public interface RazorpayClient {

    /**
     * Creates a Razorpay Order for {@code amountPaise} (INR, smallest currency
     * unit) with the given {@code receipt} reference.
     *
     * @param amountPaise the order amount in INR paise (server-calculated, never
     *        supplied by the browser)
     * @param currency    the currency code (always {@code INR})
     * @param receipt     a server-generated receipt identifier
     * @return the created Razorpay order (id, amount, currency, status, receipt)
     * @throws RazorpayClientException if the Razorpay API call fails
     */
    RazorpayOrder createOrder(BigDecimal amountPaise, String currency, String receipt);
}
