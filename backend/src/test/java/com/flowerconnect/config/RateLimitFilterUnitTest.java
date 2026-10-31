package com.flowerconnect.config;

import com.flowerconnect.config.RateLimitProperties.AuthEndpoints;
import com.flowerconnect.config.RateLimitProperties.SensitiveEndpoints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.flowerconnect.test.MutableClock;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterUnitTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private final StringWriter writer = new StringWriter();
    private Cache<String, Bucket> cache;
    private RateLimitProperties properties;
    private MutableClock clock;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        cache = Caffeine.newBuilder().build();
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.getAuth().setLoginRequestsPerHour(5);
        properties.getSensitive().setCheckoutRequestsPerHour(2);
        clock = new MutableClock(Instant.now());
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        filter = new RateLimitFilter(cache, properties, clock, objectMapper);
        when(response.getWriter()).thenReturn(new PrintWriter(writer));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledFilterPassesThroughWithoutRateLimiting() throws Exception {
        properties.setEnabled(false);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/checkout");
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(same(request), same(response));
        verify(response, never()).setStatus(429);
    }

    @Test
    void underLimitPassesThrough() throws Exception {
        authenticatedCheckout("1.2.3.4", "user-a@test.com");

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(same(request), same(response));
        verify(response, never()).setStatus(429);
    }

    @Test
    void overLimitReturns429WithRetryAfterAndCode() throws Exception {
        authenticatedCheckout("1.2.3.4", "user-a@test.com");

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), anyString());
        String body = writer.toString();
        assertTrue(body.contains("RATE_LIMITED"), "expected RATE_LIMITED code, got: " + body);
        assertTrue(body.contains("Try again in"), body);
    }

    @Test
    void authenticatedUsersHaveIndependentBuckets() throws Exception {
        authenticatedCheckout("1.2.3.4", "user-a@test.com");
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain); // user-a limit reached

        // user-a is rate limited
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(429);

        // user-b still has its own bucket
        writer.getBuffer().setLength(0);
        authenticatedCheckout("1.2.3.4", "user-b@test.com");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain, atLeastOnce()).doFilter(same(request), same(response));
        assertFalse(writer.toString().contains("RATE_LIMITED"));
    }

    @Test
    void unauthenticatedCallsAreKeyedByIp() throws Exception {
        SecurityContextHolder.clearContext();
        unauthenticatedCheckout("1.2.3.4");
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain); // limit reached

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(429);

        // different IP -> independent bucket
        writer.getBuffer().setLength(0);
        unauthenticatedCheckout("5.6.7.8");
        filter.doFilterInternal(request, response, filterChain);
        assertFalse(writer.toString().contains("RATE_LIMITED"));
    }

    @Test
    void trustedProxyHonorsXForwardedFor() throws Exception {
        properties.setTrustedProxies(List.of("10.0.0.5"));
        SecurityContextHolder.clearContext();

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/checkout");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain); // limit reached for 203.0.113.1

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(429);

        // different XFF through the same trusted proxy -> independent bucket
        writer.getBuffer().setLength(0);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.2");
        filter.doFilterInternal(request, response, filterChain);
        assertFalse(writer.toString().contains("RATE_LIMITED"));
    }

    @Test
    void untrustedProxyIgnoresXForwardedFor() throws Exception {
        SecurityContextHolder.clearContext();

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/checkout");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1");

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain); // limit reached for remoteAddr

        // same remoteAddr, different XFF -> shares bucket, still rate limited
        writer.getBuffer().setLength(0);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.2");
        filter.doFilterInternal(request, response, filterChain);
        verify(response, times(1)).setStatus(429);
        assertTrue(writer.toString().contains("RATE_LIMITED"));
    }

    @Test
    void multipartUploadPassesThroughUntouched() throws Exception {
        authenticatedCheckout("1.2.3.4", "user-a@test.com");
        when(request.getRequestURI()).thenReturn("/api/v1/vendors/products/1/images");
        when(request.getContentType()).thenReturn("multipart/form-data; boundary=----");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(same(request), same(response));
        verify(response, never()).setStatus(429);
    }

    private void authenticatedCheckout(String remoteAddr, String principal) {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/checkout");
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    private void unauthenticatedCheckout(String remoteAddr) {
        SecurityContextHolder.clearContext();
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/checkout");
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    }
}
