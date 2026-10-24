package com.flowerconnect.payment.service;

import com.flowerconnect.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies and produces the HMAC-SHA256 signatures used by the public payment
 * webhook (Phase 7, task 7.4 part 2).
 *
 * <p>The signed message is the concatenation of the {@code X-FlowerConnect-Timestamp}
 * header value and the raw request body. Verification applies a documented
 * tolerance window and a constant-time signature comparison, and never logs
 * the secret or the signature value.
 */
@Slf4j
@Service
public class WebhookSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "v1=";
    private static final String HEADER_TIMESTAMP = "X-FlowerConnect-Timestamp";
    private static final String HEADER_SIGNATURE = "X-FlowerConnect-Signature";

    private final AppProperties appProperties;
    private final Clock clock;

    private int toleranceSeconds;
    private int maxBodyBytes;

    @Autowired
    public WebhookSignatureService(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        this.toleranceSeconds = appProperties.getWebhookToleranceSeconds();
        this.maxBodyBytes = appProperties.getWebhookMaxBodyBytes();
    }

    /**
     * Returns the timestamp header name used by the webhook protocol.
     */
    public String getHeaderTimestamp() {
        return HEADER_TIMESTAMP;
    }

    /**
     * Returns the signature header name used by the webhook protocol.
     */
    public String getHeaderSignature() {
        return HEADER_SIGNATURE;
    }

    /**
     * Verifies the webhook signature for {@code timestampHeader} / {@code signatureHeader}
     * over {@code body}.
     *
     * @return {@code true} only if the signature is present, parses as {@code v1=&lt;hex&gt;},
     *         the timestamp is within the tolerance window, and the HMAC-SHA256 matches
     *         using constant-time comparison.
     */
    public boolean isValid(String secret, String timestampHeader, String signatureHeader, byte[] body) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        if (timestampHeader == null || signatureHeader == null || body == null) {
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long nowSeconds = Instant.now(clock).getEpochSecond();
        if (Math.abs(nowSeconds - timestamp) > toleranceSeconds) {
            return false;
        }

        String signature = signatureHeader.trim();
        if (!signature.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] actual = hexToBytes(signature.substring(SIGNATURE_PREFIX.length()));
        if (actual == null) {
            return false;
        }

        String payload = timestampHeader + new String(body, StandardCharsets.UTF_8);
        byte[] expected;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            expected = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }

        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Produces the {@code v1=&lt;hex&gt;} signature for {@code timestampHeader} / {@code body},
     * used by the test-mode simulator to deliver a correctly signed event through the
     * same verification path.
     */
    public String sign(String secret, String timestampHeader, byte[] body) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Webhook secret is required");
        }
        String payload = timestampHeader + new String(body, StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_PREFIX + bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    public int getToleranceSeconds() {
        return toleranceSeconds;
    }

    public int getMaxBodyBytes() {
        return maxBodyBytes;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0) {
            return null;
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            out[i / 2] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
