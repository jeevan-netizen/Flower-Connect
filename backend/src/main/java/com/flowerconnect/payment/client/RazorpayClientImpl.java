package com.flowerconnect.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Production Razorpay client backed by Spring {@link RestTemplate}.
 *
 * <p>Authenticates with HTTP Basic auth using the Razorpay Key ID and Key
 * Secret. The Key Secret is configured from {@code app.razorpay.key-secret} and
 * is never logged or returned in responses.
 */
@Slf4j
@Service
@Primary
public class RazorpayClientImpl implements RazorpayClient {

    private static final String RAZORPAY_ORDERS_URL = "https://api.razorpay.com/v1/orders";
    private static final String CURRENCY_INR = "INR";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String keyId;
    private final String keySecret;

    public RazorpayClientImpl(RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper,
                              AppProperties appProperties) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.keyId = appProperties.getRazorpayKeyId();
        this.keySecret = appProperties.getRazorpayKeySecret();
    }

    @Override
    public RazorpayOrder createOrder(BigDecimal amountPaise, String currency, String receipt) {
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new RazorpayClientException(
                    "Razorpay Key ID and Key Secret must be configured for test-mode checkout");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + base64Credentials(keyId, keySecret));
        headers.set("Content-Type", "application/json");

        Map<String, Object> body = new LinkedHashMap<>();
        // Razorpay's Orders API requires amount as an integer JSON number in
        // the smallest currency sub-unit (paise), not as a numeric string.
        body.put("amount", amountPaise.longValueExact());
        body.put("currency", currency != null ? currency : CURRENCY_INR);
        body.put("receipt", receipt);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    RAZORPAY_ORDERS_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().isError()) {
                throw new RazorpayClientException(
                        "Razorpay order creation failed with status " + response.getStatusCode());
            }

            Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
            return new RazorpayOrder(
                    (String) parsed.get("id"),
                    new BigDecimal(parsed.get("amount").toString()),
                    (String) parsed.get("currency"),
                    (String) parsed.get("status"),
                    (String) parsed.get("receipt"));
        } catch (RestClientException e) {
            throw new RazorpayClientException("Razorpay order creation request failed", e);
        } catch (Exception e) {
            throw new RazorpayClientException("Unable to parse Razorpay order response", e);
        }
    }

    private static String base64Credentials(String keyId, String keySecret) {
        String credentials = keyId + ":" + keySecret;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
