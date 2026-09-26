package com.flowerconnect.config;

import com.flowerconnect.security.util.EmailNormalizer;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Slf4j
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> RATE_LIMITED_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password"
    );

    private final Cache<String, Bucket> bucketCache;
    private final RateLimitProperties rateLimitProperties;
    private final Clock clock;

    public RateLimitFilter(Cache<String, Bucket> bucketCache,
                           RateLimitProperties rateLimitProperties,
                           Clock clock) {
        this.bucketCache = bucketCache;
        this.rateLimitProperties = rateLimitProperties;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!RATE_LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Read body once and cache it for downstream handlers
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        log.info("RateLimitFilter: path={}, request body={}", path, body);

        String email = extractEmailFromBody(body);
        log.info("RateLimitFilter: path={}, extracted email={}", path, email);
        if (email == null || email.isBlank()) {
            log.info("RateLimitFilter: email is null/blank, passing through");
            filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
            return;
        }

        String normalizedEmail = EmailNormalizer.normalize(email);
        String hashedEmail = hashEmail(normalizedEmail);
        String ip = getClientIp(request);
        String key = RateLimitConfig.buildKey(path, hashedEmail, ip);
        log.info("RateLimitFilter: bucket key={}", key);

        int limit = getLimitForPath(path);
        Bucket bucket = bucketCache.get(key, k -> RateLimitConfig.createBucket(limit, clock));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        log.info("RateLimitFilter: probe consumed={}, remainingTokens={}", probe.isConsumed(), probe.getRemainingTokens());
        if (probe.isConsumed()) {
            filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
            return;
        }

        long retryAfterNanos = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Duration.ofNanos(retryAfterNanos).getSeconds();
        if (retryAfterSeconds < 1) {
            retryAfterSeconds = 1;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String respBody = String.format(
                "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again in %d seconds.\",\"timestamp\":\"%s\"}",
                retryAfterSeconds, Instant.now(clock).toString());
        response.getWriter().write(respBody);
        log.debug("Rate limit exceeded for key={}, retryAfter={}s", key, retryAfterSeconds);
    }

    private String extractEmailFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int emailIndex = body.indexOf("\"email\"");
        if (emailIndex == -1) {
            log.info("RateLimitFilter: no email field in body");
            return null;
        }
        int colonIndex = body.indexOf(':', emailIndex);
        if (colonIndex == -1) {
            log.info("RateLimitFilter: no colon after email");
            return null;
        }
        int startQuote = body.indexOf('"', colonIndex);
        if (startQuote == -1) {
            log.info("RateLimitFilter: no opening quote after colon");
            return null;
        }
        int endQuote = body.indexOf('"', startQuote + 1);
        if (endQuote == -1) {
            log.info("RateLimitFilter: no closing quote");
            return null;
        }
        String email = body.substring(startQuote + 1, endQuote);
        log.info("RateLimitFilter: extracted email={}", email);
        return email;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private int getLimitForPath(String path) {
        RateLimitProperties.AuthEndpoints auth = rateLimitProperties.getAuth();
        return switch (path) {
            case "/api/v1/auth/login" -> auth.getLoginRequestsPerHour();
            case "/api/v1/auth/register" -> auth.getRegisterRequestsPerHour();
            case "/api/v1/auth/forgot-password" -> auth.getForgotPasswordRequestsPerHour();
            case "/api/v1/auth/reset-password" -> auth.getResetPasswordRequestsPerHour();
            default -> 5;
        };
    }

    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final String cachedBody;

        CachedBodyRequestWrapper(HttpServletRequest request, String cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new CachedServletInputStream(cachedBody.getBytes(StandardCharsets.UTF_8));
        }

        private static class CachedServletInputStream extends ServletInputStream {
            private final ByteArrayInputStream inputStream;

            CachedServletInputStream(byte[] bytes) {
                this.inputStream = new ByteArrayInputStream(bytes);
            }

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }

            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener readListener) {
                throw new UnsupportedOperationException("ReadListener not supported");
            }
        }
    }
}