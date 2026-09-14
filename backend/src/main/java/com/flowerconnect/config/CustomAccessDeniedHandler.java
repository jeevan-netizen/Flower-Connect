package com.flowerconnect.config;

import com.flowerconnect.security.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        try {
            ErrorResponse error = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpServletResponse.SC_FORBIDDEN)
                    .error("Forbidden")
                    .message("You do not have permission to access this resource")
                    .path(request.getRequestURI())
                    .build();
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), error);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
