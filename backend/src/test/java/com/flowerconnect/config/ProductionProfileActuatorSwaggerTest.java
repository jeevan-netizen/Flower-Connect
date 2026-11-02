package com.flowerconnect.config;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies production-profile exposure guarantees: Swagger/OpenAPI endpoints
 * are unavailable in {@code prod}, and the actuator surface is limited.
 * Runs against a real MySQL database so the full production configuration
 * and filter chain load.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "JWT_SECRET=test-jwt-secret-key-for-testing-phase-1-only-a-very-secure-key",
    "APP_WEBHOOK_SECRET=test-webhook-secret-key-for-testing-phase-3-plus-at-least-32-chars"
})
@Import(com.flowerconnect.config.TestClockConfig.class)
class ProductionProfileActuatorSwaggerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void swaggerApiDocsIsNotAvailableInProd() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isNotFound());
    }

    @Test
    void swaggerUiIsNotAvailableInProd() throws Exception {
        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actuatorHealthIsAvailableWithoutAuth() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorMetricsIsNotAvailableInProd() throws Exception {
        // metrics is not in management.endpoints.web.exposure.include (health,info only),
        // so the actuator layer returns 404 rather than 401/403 from Spring Security.
        mvc.perform(get("/actuator/metrics"))
                .andExpect(status().isNotFound());
    }
}
