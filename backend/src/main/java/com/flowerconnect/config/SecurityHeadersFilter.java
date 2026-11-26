package com.flowerconnect.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers to every response.
 *
 * <p>Headers applied:
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} -- prevents MIME-type sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} -- prevents clickjacking via frames.</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} -- limits referrer information.</li>
 *   <li>{@code Permissions-Policy} -- disables powerful browser features by default.</li>
 *   <li>{@code Strict-Transport-Security} -- HSTS; browsers enforce it only over HTTPS, so it is
 *       safe to emit on all responses.</li>
 *   <li>{@code Cache-Control: no-store, no-cache} -- on authentication and authenticated responses,
 *       so sensitive data is not cached by browsers or intermediate proxies.</li>
 * </ul>
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String NO_STORE = "no-store, no-cache, must-revalidate";
    private static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=(), ";
    // "magnetometer=(), gyroscope=(), accelerometer=(), ambient-light-sensor=(),";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        filterChain.doFilter(request, response);

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        String path = request.getRequestURI();
        boolean isAuthPath = path.startsWith("/api/v1/auth/");
        boolean isAuthenticated = request.getHeader("Authorization") != null;
        if (isAuthPath || isAuthenticated) {
            response.setHeader("Cache-Control", NO_STORE);
        }
    }
}
