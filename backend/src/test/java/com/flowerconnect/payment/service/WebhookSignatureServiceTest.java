package com.flowerconnect.payment.service;

import com.flowerconnect.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link WebhookSignatureService}: valid signatures are accepted,
 * missing/bad signatures, wrong secrets, modified bodies, stale timestamps and
 * replayed timestamps are rejected, and the comparison is constant-time (no
 * secret or signature value is logged).
 */
@ExtendWith(MockitoExtension.class)
class WebhookSignatureServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private WebhookSignatureService service(long nowSeconds, int toleranceSeconds) {
        AppProperties app = new AppProperties();
        app.setWebhookToleranceSeconds(toleranceSeconds);
        Clock clock = Clock.fixed(Instant.ofEpochSecond(nowSeconds), ZoneOffset.UTC);
        WebhookSignatureService s = new WebhookSignatureService(app, clock);
        ReflectionTestUtils.invokeMethod(s, "init");
        return s;
    }

    private String signedBody(WebhookSignatureService s, String timestamp, byte[] body) {
        return s.sign(SECRET, timestamp, body);
    }

    @Test
    void validSignatureIsAccepted() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "{\"paymentGroupId\":\"g-1\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L);
        String signature = signedBody(s, timestamp, body);

        assertThat(s.isValid(SECRET, timestamp, signature, body)).isTrue();
    }

    @Test
    void validSignatureJustInsideToleranceIsAccepted() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L + 299);
        String signature = signedBody(s, timestamp, body);

        assertThat(s.isValid(SECRET, timestamp, signature, body)).isTrue();
    }

    @Test
    void staleTimestampJustOutsideToleranceIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L + 301);
        String signature = signedBody(s, timestamp, body);

        assertThat(s.isValid(SECRET, timestamp, signature, body)).isFalse();
    }

    @Test
    void missingSignatureHeaderIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        assertThat(s.isValid(SECRET, Long.toString(1_700_000_000L), null, "body".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void missingTimestampHeaderIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        assertThat(s.isValid(SECRET, null, "v1=abc", "body".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void wrongSecretIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L);
        String signature = signedBody(s, timestamp, body);

        assertThat(s.isValid("wrong-secret-0123456789abcdef0123456789ab", timestamp, signature, body)).isFalse();
    }

    @Test
    void modifiedBodyIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "{\"paymentGroupId\":\"g-1\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L);
        String signature = signedBody(s, timestamp, body);

        byte[] tampered = "{\"paymentGroupId\":\"g-2\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(s.isValid(SECRET, timestamp, signature, tampered)).isFalse();
    }

    @Test
    void malformedSignatureIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        assertThat(s.isValid(SECRET, Long.toString(1_700_000_000L), "not-hex", "body".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void emptySecretIsRejected() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        assertThat(s.isValid("", Long.toString(1_700_000_000L), "v1=abc", "body".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(s.isValid(null, Long.toString(1_700_000_000L), "v1=abc", "body".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void signProducesVerifiableSignature() {
        WebhookSignatureService s = service(1_700_000_000L, 300);
        byte[] body = "body".getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(1_700_000_000L);
        String signature = s.sign(SECRET, timestamp, body);

        assertThat(signature).startsWith("v1=");
        assertThat(s.isValid(SECRET, timestamp, signature, body)).isTrue();
    }
}
