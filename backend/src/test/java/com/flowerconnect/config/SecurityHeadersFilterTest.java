package com.flowerconnect.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SecurityHeadersFilterTest {

    @Test
    void addsSecurityHeadersToEveryResponse() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/v1/locations");
        when(request.getHeader("Authorization")).thenReturn(null);

        Map<String, String> headers = new HashMap<>();
        doAnswer(inv -> {
            headers.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(headers.get("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(headers.get("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.get("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
        assertThat(headers.get("Permissions-Policy")).startsWith("camera=(), microphone=(), geolocation=()");
        assertThat(headers.get("Strict-Transport-Security")).startsWith("max-age=31536000");
    }

    @Test
    void authResponseGetsCacheControlNoStore() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getHeader("Authorization")).thenReturn(null);

        Map<String, String> headers = new HashMap<>();
        doAnswer(inv -> {
            headers.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());

        filter.doFilterInternal(request, response, chain);

        assertThat(headers.get("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
    }

    @Test
    void authenticatedRequestGetsCacheControlNoStore() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/v1/orders");
        when(request.getHeader("Authorization")).thenReturn("Bearer some-token");

        Map<String, String> headers = new HashMap<>();
        doAnswer(inv -> {
            headers.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());

        filter.doFilterInternal(request, response, chain);

        assertThat(headers.get("Cache-Control")).isEqualTo("no-store, no-cache, must-revalidate");
    }

    @Test
    void nonAuthRequestWithoutAuthHeaderDoesNotGetCacheControlNoStore() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/api/v1/locations");
        when(request.getHeader("Authorization")).thenReturn(null);

        Map<String, String> headers = new HashMap<>();
        doAnswer(inv -> {
            headers.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(response).setHeader(anyString(), anyString());

        filter.doFilterInternal(request, response, chain);

        assertThat(headers.containsKey("Cache-Control")).isFalse();
    }
}
