package com.flowerconnect.notification.sse;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE stream of the authenticated caller's own notification updates (plan Phase
 * 9, task 9.5).
 *
 * <p>The recipient is derived from the authenticated principal (the JWT
 * subject) -- the request never carries a user id, so cross-user subscription
 * is not expressible. The endpoint sits under the existing
 * {@code /api/v1/notifications/**} namespace, already mapped to
 * {@code hasAnyRole("CUSTOMER", "FLORIST", "ADMIN")} in {@code SecurityConfig},
 * so no security-config change is required.
 *
 * <p>Response headers disable buffering (cache-control, keep-alive,
 * {@code X-Accel-Buffering: no}) so proxies and the servlet container stream
 * each event to the client as it arrives rather than buffering the open
 * connection.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationSseController {

    private final NotificationSseService sseService;
    private final UserRepository userRepository;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        String email = authentication.getName();
        User user = userRepository.findByEmailWithRole(email)
                .orElseThrow(() -> BusinessException.notFound("Unknown principal: " + email));

        log.debug("SSE stream subscribed for user {} ({})", user.getId(), email);
        return sseService.subscribe(user.getId());
    }
}
