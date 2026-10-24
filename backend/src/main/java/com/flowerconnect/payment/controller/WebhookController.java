package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.payment.dto.WebhookEvent;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.payment.service.WebhookSignatureService;
import com.flowerconnect.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Public payment webhook (Phase 7, task 7.4 part 2).
 *
 * <p>{@code POST /api/v1/payments/webhook} is the only new public write
 * endpoint. It is signature-authenticated (HMAC-SHA256 over the timestamp and
 * raw body) and requires no JWT. The raw body is read and verified before any
 * parsing or intent lookup, and a body-size limit is enforced.
 *
 * <p>Response policy:
 * <ul>
 *   <li>Invalid / missing / incorrect signature, stale timestamp, oversized
 *       body -> rejected 4xx, no state change, no secret in the response.</li>
 *   <li>Unknown intent -> 404, no information leakage.</li>
 *   <li>Duplicate event ID -> 200, no repeated side effects.</li>
 *   <li>Accepted success/failure event -> 200, applied atomically.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class WebhookController {

    private final PaymentService paymentService;
    private final WebhookSignatureService signatureService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @PostMapping("/webhook")
    public ResponseEntity<WebhookResponse> handleWebhook(HttpServletRequest request) throws IOException {
        byte[] body = readBody(request);

        String timestamp = request.getHeader(signatureService.getHeaderTimestamp());
        String signature = request.getHeader(signatureService.getHeaderSignature());

        // Verify the signature before parsing or looking up an intent.
        if (!signatureService.isValid(appProperties.getWebhookSecret(), timestamp, signature, body)) {
            // Generic message: no secret or sensitive value in the response.
            log.info("Webhook rejected: invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(WebhookResponse.builder()
                            .outcome("rejected")
                            .providerEventId(null)
                            .paymentGroupId(null)
                            .intentStatus(null)
                            .build());
        }

        WebhookEvent event;
        try {
            event = objectMapper.readValue(body, WebhookEvent.class);
        } catch (IOException e) {
            log.info("Webhook rejected: unparseable body");
            throw BusinessException.badRequest("Malformed webhook payload");
        }

        PaymentService.WebhookResult result = paymentService.processProviderEvent(
                event.getPaymentGroupId(),
                event.getProviderEventId(),
                event.getEventType(),
                event.getOutcome(),
                event.getAmount());

        return ResponseEntity.ok(WebhookResponse.from(result));
    }

    /**
     * Reads the raw request body, enforcing the configured size limit.
     */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        int max = appProperties.getWebhookMaxBodyBytes();
        ByteArrayOutputStream buf = new ByteArrayOutputStream(max);
        byte[] chunk = new byte[8192];
        int read;
        long total = 0;
        while ((read = request.getInputStream().read(chunk)) != -1) {
            total += read;
            if (total > max) {
                throw BusinessException.payloadTooLarge("Webhook body exceeds the configured size limit");
            }
            buf.write(chunk, 0, read);
        }
        return buf.toByteArray();
    }
}
