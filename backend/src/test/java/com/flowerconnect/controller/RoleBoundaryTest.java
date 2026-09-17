package com.flowerconnect.controller;

import com.flowerconnect.test.RoleBoundaryTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the {@link RoleBoundaryTester} utility against the two access-control
 * patterns defined in the production SecurityConfig:
 * <ul>
 *   <li>/api/v1/users/me — authenticated (any role → 200, unauthenticated → 401)</li>
 *   <li>/actuator/metrics — ADMIN only (ADMIN → 200, FLORIST/CUSTOMER → 403)</li>
 * </ul>
 *
 * Uses a self-contained {@link TestProbeController} and {@link TestSecurityConfig}
 * that mirror the production rules. Does not modify any existing test.
 */
@WebMvcTest(controllers = TestProbeController.class)
@Import(RoleBoundaryTest.TestSecurityConfig.class)
class RoleBoundaryTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/**").permitAll()
                            .requestMatchers("/api/v1/users/me").authenticated()
                            .requestMatchers("/actuator/metrics").hasRole("ADMIN")
                            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                            .requestMatchers("/actuator/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    private RoleBoundaryTester tester;

    @BeforeEach
    void setUp() {
        tester = new RoleBoundaryTester(mockMvc);
    }

    @Test
    void usersMeIsAccessibleToAllRoles() {
        tester.assertAllRoles("/api/v1/users/me", HttpMethod.GET, 200, 200, 200);
    }

    @Test
    void usersMeRequiresAuthentication() {
        tester.assertUnauthenticated("/api/v1/users/me", HttpMethod.GET, 401);
    }

    @Test
    void actuatorMetricsIsAdminOnly() {
        tester.assertAllRoles("/actuator/metrics", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    void actuatorMetricsRequiresAuthentication() {
        tester.assertUnauthenticated("/actuator/metrics", HttpMethod.GET, 401);
    }
}
