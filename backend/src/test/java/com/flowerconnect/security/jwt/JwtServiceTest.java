package com.flowerconnect.security.jwt;

import com.flowerconnect.config.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("test-jwt-secret-key-for-testing-phase-1-only-a-very-secure-key!");
        props.setAccessTtlMs(900000L);
        props.setRefreshTtlMs(604800000L);
        jwtService = new JwtService(props);
        jwtService.init();
    }

    @Test
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken("user@test.com", "CUSTOMER", List.of("ROLE_CUSTOMER"));
        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        assertEquals("user@test.com", jwtService.getEmailFromToken(token));
        assertEquals("CUSTOMER", jwtService.getRoleFromToken(token));
    }

    @Test
    void shouldReturnAuthoritiesInToken() {
        String token = jwtService.generateAccessToken("user@test.com", "CUSTOMER", List.of("ROLE_CUSTOMER"));
        List<String> authorities = jwtService.getAuthoritiesFromToken(token);
        assertEquals(1, authorities.size());
        assertEquals("ROLE_CUSTOMER", authorities.get(0));
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenNull() {
        String token = jwtService.generateAccessToken("user@test.com", "ADMIN", null);
        List<String> authorities = jwtService.getAuthoritiesFromToken(token);
        assertTrue(authorities.isEmpty());
    }

    @Test
    void shouldReturnAccessTtlMs() {
        assertEquals(900000L, jwtService.getAccessTtlMs());
    }

    @Test
    void shouldGenerateRefreshToken() {
        String token1 = jwtService.generateRefreshToken();
        String token2 = jwtService.generateRefreshToken();
        assertNotNull(token1);
        assertNotNull(token2);
        assertNotEquals(token1, token2);
        assertTrue(token1.length() >= 32);
    }

    @Test
    void shouldValidateValidToken() {
        String token = jwtService.generateAccessToken("user@test.com", "CUSTOMER", List.of("ROLE_CUSTOMER"));
        assertTrue(jwtService.validateToken(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        assertFalse(jwtService.validateToken("invalid.token.here"));
    }

    @Test
    void shouldReturnFalseForMalformedToken() {
        assertFalse(jwtService.validateToken("malformed-token"));
    }

    @Test
    void shouldThrowJwtAuthenticationExceptionForInvalidTokenOnGetEmail() {
        assertThrows(com.flowerconnect.exception.JwtAuthenticationException.class,
                () -> jwtService.getEmailFromToken("invalid.token.here"));
    }

    @Test
    void shouldThrowJwtAuthenticationExceptionForInvalidTokenOnGetRole() {
        assertThrows(com.flowerconnect.exception.JwtAuthenticationException.class,
                () -> jwtService.getRoleFromToken("invalid.token.here"));
    }

    @Test
    void shouldThrowWhenSecretTooShort() {
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret("short");
        JwtService service = new JwtService(shortProps);
        assertThrows(IllegalStateException.class, service::init);
    }
}
