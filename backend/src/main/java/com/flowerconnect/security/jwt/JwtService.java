package com.flowerconnect.security.jwt;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.exception.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String email, String role, Collection<String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("authorities", authorities != null ? authorities : Collections.emptyList());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTtlMs());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | JwtAuthenticationException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            throw new JwtAuthenticationException("Invalid or expired JWT token", e);
        }
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = parseClaims(token);
        Object auths = claims.get("authorities");
        if (auths instanceof Collection<?>) {
            List<String> result = new ArrayList<>();
            for (Object o : (Collection<?>) auths) {
                result.add(String.valueOf(o));
            }
            return result;
        }
        return Collections.emptyList();
    }

    public long getAccessTtlMs() {
        return jwtProperties.getAccessTtlMs();
    }
}
