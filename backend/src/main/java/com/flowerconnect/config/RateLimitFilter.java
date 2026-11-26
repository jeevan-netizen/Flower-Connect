package com.flowerconnect.config;

import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.security.dto.ErrorResponse;
import com.flowerconnect.security.util.EmailNormalizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Slf4j
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = false)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // Anonymous endpoints (no authenticated caller yet): keyed by email from the
    // request body when present, otherwise by client IP. A missing email no
    // longer bypasses the limit.
    private static final List<RateLimitEndpoint> ANON_EMAIL_ENDPOINTS = List.of(
            endpoint("POST", "/api/v1/auth/login", p -> p.getAuth().getLoginRequestsPerHour()),
            endpoint("POST", "/api/v1/auth/register", p -> p.getAuth().getRegisterRequestsPerHour()),
            endpoint("POST", "/api/v1/auth/forgot-password", p -> p.getAuth().getForgotPasswordRequestsPerHour()),
            endpoint("POST", "/api/v1/auth/reset-password", p -> p.getAuth().getResetPasswordRequestsPerHour())
    );

    // Public, signature-authenticated webhook: keyed by client IP only.
    private static final RateLimitEndpoint WEBHOOK_ENDPOINT =
            endpoint("POST", "/api/v1/payments/webhook", EndpointKind.ANON_IP,
                    RateLimitProperties::getWebhookRequestsPerHour);

    // Mutating endpoints that require an authenticated caller: keyed by the
    // authenticated principal (email) so each user has an independent bucket;
    // unauthenticated calls fall back to the client IP.
    private static final List<RateLimitEndpoint> AUTH_USER_ENDPOINTS = List.of(
            endpoint("POST", "/api/v1/checkout", EndpointKind.AUTH_USER, p -> p.getSensitive().getCheckoutRequestsPerHour()),
            endpoint("POST", "/api/v1/checkout/quote", EndpointKind.AUTH_USER, p -> p.getSensitive().getCheckoutQuoteRequestsPerHour()),
            endpoint("POST", "/api/v1/disputes/orders/{orderId}", EndpointKind.AUTH_USER, p -> p.getSensitive().getDisputeRequestsPerHour()),
            endpoint("POST", "/api/v1/orders/{orderId}/reviews", EndpointKind.AUTH_USER, p -> p.getSensitive().getReviewRequestsPerHour()),
            endpoint("POST", "/api/v1/addresses", EndpointKind.AUTH_USER, p -> p.getSensitive().getAddressRequestsPerHour()),
            endpoint("POST", "/api/v1/cart/items", EndpointKind.AUTH_USER, p -> p.getSensitive().getCartMutationRequestsPerHour()),
            endpoint("PATCH", "/api/v1/cart/items/{id}", EndpointKind.AUTH_USER, p -> p.getSensitive().getCartMutationRequestsPerHour()),
            endpoint("DELETE", "/api/v1/cart/items/{id}", EndpointKind.AUTH_USER, p -> p.getSensitive().getCartMutationRequestsPerHour()),
            endpoint("DELETE", "/api/v1/cart", EndpointKind.AUTH_USER, p -> p.getSensitive().getCartMutationRequestsPerHour()),
            endpoint("POST", "/api/v1/vendors/products/{productId}/images", EndpointKind.AUTH_USER, p -> p.getSensitive().getUploadRequestsPerHour()),
            endpoint("POST", "/api/v1/pos/sales", EndpointKind.AUTH_USER, p -> p.getSensitive().getPosSaleRequestsPerHour())
    );

    private final Cache<String, Bucket> bucketCache;
    private final RateLimitProperties rateLimitProperties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(Cache<String, Bucket> bucketCache,
                           RateLimitProperties rateLimitProperties,
                           Clock clock,
                           ObjectMapper objectMapper) {
        this.bucketCache = bucketCache;
        this.rateLimitProperties = rateLimitProperties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!rateLimitProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        RateLimitEndpoint endpoint = match(path, method);
        if (endpoint == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only anonymous-email endpoints need the body (to extract an email key).
        // The body is read once and replayed through a wrapper so downstream
        // handlers can still consume it. Multipart requests (uploads) are never
        // buffered here -- they pass through untouched.
        boolean needsBody = endpoint.kind() == EndpointKind.ANON_EMAIL;
        String body = needsBody ? readBody(request) : null;
        boolean multipart = isMultipart(request);

        String key = buildKey(request, endpoint, body);
        int limit = endpoint.limit(rateLimitProperties);
        Bucket bucket = bucketCache.get(key, k -> RateLimitConfig.createBucket(limit, clock));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            if (needsBody && !multipart) {
                filterChain.doFilter(new CachedBodyRequestWrapper(request, body == null ? "" : body), response);
            } else {
                filterChain.doFilter(request, response);
            }
            return;
        }

        writeRateLimited(response, request, probe);
    }

    private RateLimitEndpoint match(String path, String method) {
        for (RateLimitEndpoint e : ANON_EMAIL_ENDPOINTS) {
            if (e.method().equalsIgnoreCase(method) && PATH_MATCHER.match(e.pattern(), path)) {
                return e;
            }
        }
        if (WEBHOOK_ENDPOINT.method().equalsIgnoreCase(method) && PATH_MATCHER.match(WEBHOOK_ENDPOINT.pattern(), path)) {
            return WEBHOOK_ENDPOINT;
        }
        for (RateLimitEndpoint e : AUTH_USER_ENDPOINTS) {
            if (e.method().equalsIgnoreCase(method) && PATH_MATCHER.match(e.pattern(), path)) {
                return e;
            }
        }
        return null;
    }

    private String buildKey(HttpServletRequest request, RateLimitEndpoint endpoint, String body) {
        String path = request.getRequestURI();
        if (endpoint.kind() == EndpointKind.ANON_EMAIL) {
            String email = extractEmailFromBody(body);
            String ip = getClientIp(request);
            String identity = (email != null && !email.isBlank())
                    ? "email:" + hashEmail(EmailNormalizer.normalize(email)) + ":ip:" + ip
                    : "ip:" + ip;
            return "anon:" + path + ":" + identity;
        }
        if (endpoint.kind() == EndpointKind.ANON_IP) {
            return "anon:" + path + ":ip:" + getClientIp(request);
        }
        // AUTH_USER: key by the authenticated principal, with an IP fallback.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authenticatedPrincipal(authentication);
        if (principal != null) {
            return "user:" + path + ":user:" + hashEmail(principal);
        }
        return "user:" + path + ":ip:" + getClientIp(request);
    }

    private String authenticatedPrincipal(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String name) {
            return name.isBlank() ? null : name;
        }
        String name = authentication.getName();
        return name.isBlank() ? null : name;
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrusted(remoteAddr)) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp;
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private boolean isTrusted(String remoteAddr) {
        List<String> trusted = rateLimitProperties.getTrustedProxies();
        if (trusted == null || trusted.isEmpty() || remoteAddr == null) {
            return false;
        }
        return trusted.contains(remoteAddr);
    }

    private boolean isMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("multipart/");
    }

    private String readBody(HttpServletRequest request) throws IOException {
        return StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
    }

    private String extractEmailFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int emailIndex = body.indexOf("\"email\"");
        if (emailIndex == -1) {
            return null;
        }
        int colonIndex = body.indexOf(':', emailIndex);
        if (colonIndex == -1) {
            return null;
        }
        int startQuote = body.indexOf('"', colonIndex);
        if (startQuote == -1) {
            return null;
        }
        int endQuote = body.indexOf('"', startQuote + 1);
        if (endQuote == -1) {
            return null;
        }
        return body.substring(startQuote + 1, endQuote);
    }

    private void writeRateLimited(HttpServletResponse response, HttpServletRequest request, ConsumptionProbe probe)
            throws IOException {
        long retryAfterNanos = probe.getNanosToWaitForRefill();
        long retryAfterSeconds = Duration.ofNanos(retryAfterNanos).getSeconds();
        if (retryAfterSeconds < 1) {
            retryAfterSeconds = 1;
        }

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now(clock))
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error("Too Many Requests")
                .errorCode(ErrorCode.RATE_LIMITED)
                .message("Too many requests. Try again in " + retryAfterSeconds + " seconds.")
                .path(request.getRequestURI())
                .build();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
        log.debug("Rate limit exceeded, retryAfter={}s", retryAfterSeconds);
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

    private static RateLimitEndpoint endpoint(String method, String pattern,
                                              Function<RateLimitProperties, Integer> limitSupplier) {
        return new RateLimitEndpoint(method, pattern, EndpointKind.ANON_EMAIL, limitSupplier);
    }

    private static RateLimitEndpoint endpoint(String method, String pattern, EndpointKind kind,
                                              Function<RateLimitProperties, Integer> limitSupplier) {
        return new RateLimitEndpoint(method, pattern, kind, limitSupplier);
    }

    private record RateLimitEndpoint(String method, String pattern, EndpointKind kind,
                                     Function<RateLimitProperties, Integer> limitSupplier) {
        int limit(RateLimitProperties properties) {
            return limitSupplier.apply(properties);
        }
    }

    private enum EndpointKind {
        ANON_EMAIL, ANON_IP, AUTH_USER
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
