package com.flowerconnect.config;

import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.security.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) {
        try {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now(clock))
                    .status(HttpServletResponse.SC_FORBIDDEN)
                    .error("Forbidden")
                    .errorCode(ErrorCode.FORBIDDEN)
                    .message("You do not have permission to access this resource")
                    .path(request.getRequestURI())
                    .build();
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            log.debug("Failed to write error response: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
