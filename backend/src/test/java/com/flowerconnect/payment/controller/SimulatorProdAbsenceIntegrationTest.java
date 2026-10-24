package com.flowerconnect.payment.controller;

import com.flowerconnect.payment.provider.SimulatedPaymentProvider;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the simulated pay/fail components are absent in the {@code prod}
 * profile (task 7.4 part 2). Runs against a real MySQL database so the full
 * production filter chain and configuration classes load.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
    "JWT_SECRET=test-jwt-secret-key-for-testing-phase-1-only-a-very-secure-key",
    "APP_WEBHOOK_SECRET=0123456789abcdef0123456789abcdef01"
})
@Import(com.flowerconnect.config.TestClockConfig.class)
class SimulatorProdAbsenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.context.ApplicationContext context;

    @Test
    void simulatorComponentsAreAbsentInProd() {
        assertThat(context.getBeanNamesForType(SimulatedPaymentProvider.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SimulatorController.class)).isEmpty();
    }

    @Test
    void simulatorRouteIsNotRegisteredInProd() throws Exception {
        mvc.perform(post("/api/v1/payments/simulator/1/pay"))
            .andExpect(status().isNotFound());
    }
}
