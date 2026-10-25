package com.flowerconnect.notification.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Delivery backend for the notification SSE stream (plan Phase 9, task 9.5).
 *
 * <p>One {@link SseEmitter} per open connection, keyed by the recipient's user
 * id. Multiple connections per user are supported (capped by
 * {@code app.notification.sse-max-connections-per-user}); the application
 * design deliberately allows more than one tab or device to subscribe to the
 * same inbox.
 *
 * <p>Lifecycle is managed strictly inside this service: every emitter registers
 * {@code onCompletion}/{@code onTimeout}/{@code onError} callbacks that remove
 * it, and a scheduled heartbeat sends a comment event to every live emitter and
 * drops any whose send fails. A slow or disconnected client can therefore never
 * block delivery to other clients, and a delivery failure here never reaches
 * the caller -- notification persistence, email delivery and the originating
 * business transaction are all unaffected.
 *
 * <p>Delivery is best-effort: there is no replay and no exactly-once promise.
 * Reconnects recover through the existing {@code GET /api/v1/notifications}
 * list and unread-count API.
 */
@Slf4j
@Service
public class NotificationSseService {

    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public NotificationSseService(ObjectMapper objectMapper, AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    /**
     * Registers a new SSE emitter for the given user and returns it. The
     * emitter is wired up for completion/timeout/error cleanup before it is
     * handed to the caller, so the caller only has to return it from the
     * controller method.
     *
     * @return the emitter, or an already-completed emitter when the user has
     *         reached the per-user connection cap
     */
    public SseEmitter subscribe(Long userId) {
        int maxConnections = appProperties.getSseMaxConnectionsPerUser();
        CopyOnWriteArrayList<SseEmitter> userEmitters = emittersByUser.computeIfAbsent(userId,
                key -> new CopyOnWriteArrayList<>());
        if (userEmitters.size() >= maxConnections) {
            log.warn("User {} already has {} SSE connections (max {}); refusing another",
                    userId, userEmitters.size(), maxConnections);
            SseEmitter refused = new SseEmitter(0L);
            refused.complete();
            return refused;
        }

        SseEmitter emitter = new SseEmitter(timeoutMillis());
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError((ex) -> {
            log.warn("SSE error for user {}; removing emitter", userId, ex);
            remove(userId, emitter);
        });
        userEmitters.add(emitter);
        log.debug("SSE subscriber registered for user {} ({} total)", userId, userEmitters.size());
        return emitter;
    }

    /**
     * Pushes one notification to every live emitter of the given user. A failed
     * send removes that emitter so it cannot stall the others; the failure is
     * logged and swallowed and never propagates to the caller.
     */
    public void emit(Long userId, NotificationResponse notification) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emittersByUser.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        String data;
        try {
            data = objectMapper.writeValueAsString(notification);
        } catch (Exception ex) {
            log.error("Failed to serialise notification for SSE delivery to user {}", userId, ex);
            return;
        }

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(notification.getId()))
                        .name("notification")
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                // Slow or gone client: drop it so the remaining clients are not blocked.
                remove(userId, emitter);
            }
        }
    }

    /**
     * Sends a heartbeat comment to every live emitter and removes any emitter
     * whose send fails. Scheduled via {@code app.notification.sse-heartbeat-cron};
     * disabled in the test profile (cron {@code -}).
     */
    @Scheduled(cron = "${app.notification.sse-heartbeat-cron:#{'0/30 * * * * *'}}")
    public void heartbeat() {
        for (var entry : emittersByUser.entrySet()) {
            Long userId = entry.getKey();
            CopyOnWriteArrayList<SseEmitter> userEmitters = entry.getValue();
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception ex) {
                    remove(userId, emitter);
                }
            }
            emittersByUser.computeIfPresent(userId, (id, remaining) ->
                    remaining.isEmpty() ? null : remaining);
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfPresent(userId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    /** Number of open SSE connections for the given user (for tests/observability). */
    public int connectionCount(Long userId) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByUser.get(userId);
        return list == null ? 0 : list.size();
    }

    private long timeoutMillis() {
        return (long) appProperties.getSseTimeoutSeconds() * 1000L;
    }
}
