package com.flowerconnect.security.jwt;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                    ObjectMapper objectMapper, Clock clock) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                if (jwtService.validateToken(token)) {
                    String email = jwtService.getEmailFromToken(token);

                    User user = userRepository.findByEmail(email).orElse(null);
                    if (user == null) {
                        log.debug("JWT authentication failed: user not found");
                        writeUnauthorized(response, request, "Invalid or expired token");
                        return;
                    }
                    if (user.getStatus() == User.Status.SUSPENDED) {
                        log.debug("JWT authentication failed: user {} is suspended", user.getEmail());
                        writeForbidden(response, request);
                        return;
                    }
                    if (user.getStatus() != User.Status.ACTIVE) {
                        log.debug("JWT authentication failed: user {} is not active", user.getEmail());
                        writeUnauthorized(response, request, "Invalid or expired token");
                        return;
                    }

                    List<String> authorities = jwtService.getAuthoritiesFromToken(token);

                    List<SimpleGrantedAuthority> granted = authorities.stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            email, null, granted);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.debug("JWT authentication failed: {}", e.getMessage());
                writeUnauthorized(response, request, "Invalid or expired token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .error("Unauthorized")
                .errorCode(ErrorCode.UNAUTHORIZED)
                .message(message)
                .path(request.getRequestURI())
                .build();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }

    private void writeForbidden(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpServletResponse.SC_FORBIDDEN)
                .error("Forbidden")
                .errorCode(ErrorCode.ACCOUNT_SUSPENDED)
                .message("Account suspended")
                .path(request.getRequestURI())
                .build();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
