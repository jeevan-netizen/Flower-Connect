package com.flowerconnect.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.notification.dto.NotificationResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for {@link NotificationSseService}: emitter registration, delivery,
 * and lifecycle cleanup. All assertions are deterministic (no sleeping or
 * timing): a dead client is simulated with {@link SseEmitter#completeWithError},
 * which makes subsequent sends fail so the service removes the emitter.
 */
class NotificationSseServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AppProperties appProperties = new AppProperties();
    private final NotificationSseService service = new NotificationSseService(objectMapper, appProperties);

    private static NotificationResponse notification() {
        return NotificationResponse.builder()
                .id(42L)
                .type("order_placed")
                .title("Order placed")
                .message("Your order #42 is confirmed.")
                .targetType("ORDER")
                .targetId(42L)
                .build();
    }

    @Test
    void subscribeRegistersAnEmitterForTheUser() {
        SseEmitter emitter = service.subscribe(7L);

        assertThat(emitter).isNotNull();
        assertThat(service.connectionCount(7L)).isEqualTo(1);
    }

    @Test
    void emitDeliversToEveryConnectedEmitterOfTheUser() {
        SseEmitter a = service.subscribe(7L);
        SseEmitter b = service.subscribe(7L);

        assertThatCode(() -> service.emit(7L, notification())).doesNotThrowAnyException();
        assertThat(service.connectionCount(7L)).isEqualTo(2);

        // Both emitters are still live: a successful send never removes one.
        assertThat(service.connectionCount(7L)).isEqualTo(2);
    }

    @Test
    void emitToAUserWithNoSubscribersIsANoOp() {
        assertThatCode(() -> service.emit(999L, notification())).doesNotThrowAnyException();
        assertThat(service.connectionCount(999L)).isEqualTo(0);
    }

    @Test
    void completedEmitterIsRemovedOnNextEmit() {
        SseEmitter emitter = service.subscribe(7L);
        emitter.completeWithError(new IOException("client gone"));

        assertThatCode(() -> service.emit(7L, notification())).doesNotThrowAnyException();
        assertThat(service.connectionCount(7L)).isEqualTo(0);
    }

    @Test
    void erroredEmitterIsRemovedByHeartbeat() {
        SseEmitter emitter = service.subscribe(7L);
        emitter.completeWithError(new IOException("client gone"));

        assertThat(service.connectionCount(7L)).isEqualTo(1);
        service.heartbeat();
        assertThat(service.connectionCount(7L)).isEqualTo(0);
    }

    @Test
    void heartbeatKeepsHealthyEmitters() {
        SseEmitter emitter = service.subscribe(7L);

        assertThatCode(() -> service.heartbeat()).doesNotThrowAnyException();
        assertThat(service.connectionCount(7L)).isEqualTo(1);
    }

    @Test
    void perUserConnectionCapRefusesExtraConnections() {
        appProperties.setSseMaxConnectionsPerUser(1);

        SseEmitter first = service.subscribe(7L);
        SseEmitter second = service.subscribe(7L);

        assertThat(service.connectionCount(7L)).isEqualTo(1);
        // The refused connection is completed straight away so the client can
        // reconnect later; it is never added to the user's live emitters.
        assertThatCode(() -> service.emit(7L, notification())).doesNotThrowAnyException();
        assertThat(service.connectionCount(7L)).isEqualTo(1);
    }

    @Test
    void emitNeverThrowsEvenWhenAllEmittersAreDead() {
        SseEmitter emitter = service.subscribe(7L);
        emitter.completeWithError(new IOException("client gone"));

        assertThatCode(() -> service.emit(7L, notification())).doesNotThrowAnyException();
        assertThat(service.connectionCount(7L)).isEqualTo(0);
    }
}
