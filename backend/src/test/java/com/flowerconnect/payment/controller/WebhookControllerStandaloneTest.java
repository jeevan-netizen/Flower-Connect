package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.payment.dto.WebhookEvent;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.payment.service.WebhookSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone test for {@link WebhookController} signature verification. Runs
 * without a database/Docker: the controller is built with a real
 * {@link WebhookSignatureService} and a mocked {@link PaymentService}.
 */
class WebhookControllerStandaloneTest {

    private static final String SECRET = "test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars";

    private WebhookSignatureService signatureService() {
        AppProperties app = new AppProperties();
        app.setWebhookSecret(SECRET);
        return new WebhookSignatureService(app, Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }

    private static byte[] hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSA256".replace("SA", "A")));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private MvcResult webhook(MockMvc mvc, String paymentGroupId, String providerEventId, String outcome,
                              boolean tamperBody) throws Exception {
        WebhookEvent event = WebhookEvent.builder()
            .providerEventId(providerEventId).paymentGroupId(paymentGroupId)
            .eventType("payment.succeeded").outcome(outcome).amount(new BigDecimal("299.00")).build();
        String body = new ObjectMapper().writeValueAsString(event);
        long now = Instant.now().getEpochSecond();
        String signature = "v1=" + hex(hmac(SECRET, Long.toString(now) + body));
        String content = tamperBody ? "{\"tampered\":true}" : body;
        return mvc.perform(post("/api/v1/payments/webhook")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(content.getBytes(StandardCharsets.UTF_8))
            .header("X-FlowerConnect-Timestamp", Long.toString(now))
            .header("X-FlowerConnect-Signature", signature)).andReturn();
    }

    @Test
    void validSignatureCallsServiceAndReturns200() throws Exception {
        PaymentService service = mock(PaymentService.class);
        when(service.processProviderEvent(anyString(), anyString(), anyString(), anyString(), any(BigDecimal.class)))
            .thenReturn(PaymentService.WebhookResult.accepted("evt-1", "group-1", "PAID"));

        AppProperties app = new AppProperties();
        app.setWebhookSecret(SECRET);
        WebhookController controller = new WebhookController(
            service, new WebhookSignatureService(app, Clock.fixed(Instant.now(), ZoneOffset.UTC)), app,
            new ObjectMapper());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult res = webhook(mvc, "group-1", "evt-1", "success", false);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        WebhookResponse body = new ObjectMapper().readValue(res.getResponse().getContentAsString(), WebhookResponse.class);
        assertThat(body.getOutcome()).isEqualTo("accepted");
        verify(service).processProviderEvent("group-1", "evt-1", "payment.succeeded", "success", new BigDecimal("299.00"));
    }

    @Test
    void invalidSignatureReturns401WithoutCallingService() throws Exception {
        PaymentService service = mock(PaymentService.class);
        AppProperties app = new AppProperties();
        app.setWebhookSecret(SECRET);
        WebhookController controller = new WebhookController(
            service, new WebhookSignatureService(app, Clock.fixed(Instant.now(), ZoneOffset.UTC)), app,
            new ObjectMapper());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult res = webhook(mvc, "group-1", "evt-1", "success", true);
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        verify(service, never()).processProviderEvent(any(), any(), any(), any(), any());
    }
}
