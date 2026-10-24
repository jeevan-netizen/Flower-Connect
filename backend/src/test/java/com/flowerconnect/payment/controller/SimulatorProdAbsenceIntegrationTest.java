package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.payment.provider.SimulatedPaymentProvider;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    private static final String PASSWORD = "password123";

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper json;

    @Test
    void simulatorComponentsAreAbsentInProd() {
        assertThat(context.getBeanNamesForType(SimulatedPaymentProvider.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SimulatorController.class)).isEmpty();
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void simulatorRouteIsNotRegisteredInProd() throws Exception {
        // With the simulator controller profiled out, an authenticated customer's
        // request passes Spring Security (/api/v1/payments/** -> CUSTOMER) but
        // finds no MVC handler, so the dispatcher returns 404 rather than 401.
        User customer = users.saveAndFlush(User.builder()
            .email("sim-prod-" + java.util.UUID.randomUUID() + "@t.com")
            .passwordHash(encoder.encode(PASSWORD))
            .fullName("Sim Prod Customer")
            .phone("+91" + java.util.concurrent.ThreadLocalRandom.current().nextLong(1_0000_0000L, 9_9999_9999L))
            .role(roles.findByName("CUSTOMER").orElseThrow())
            .status(User.Status.ACTIVE).build());
        String token = login(customer);

        mvc.perform(post("/api/v1/payments/simulator/1/pay")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    private String login(User u) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(
                new LoginRequest() {{
                    setEmail(u.getEmail());
                    setPassword(PASSWORD);
                }})))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
