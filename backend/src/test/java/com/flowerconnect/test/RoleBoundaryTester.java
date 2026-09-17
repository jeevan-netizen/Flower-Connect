package com.flowerconnect.test;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reusable utility for role-based access control testing.
 * <p>
 * Given a MockMvc instance, sends the same request as CUSTOMER, VENDOR,
 * and ADMIN in turn and asserts the expected HTTP status for each role.
 * </p>
 * <p>
 * Unauthenticated requests can also be tested via {@link #assertUnauthenticated(MockHttpServletRequestBuilder, int)}.
 * </p>
 */
public final class RoleBoundaryTester {

    private final MockMvc mockMvc;

    public RoleBoundaryTester(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * Asserts that each role receives the expected status code when hitting
     * the given endpoint with the given HTTP method.
     *
     * @param endpoint         the URL path (e.g. "/api/v1/users/me")
     * @param method           the HTTP method (e.g. HttpMethod.GET)
     * @param customerStatus   expected status for CUSTOMER role
     * @param vendorStatus     expected status for VENDOR/FLORIST role
     * @param adminStatus      expected status for ADMIN role
     */
    public void assertAllRoles(String endpoint, HttpMethod method,
                               int customerStatus, int vendorStatus, int adminStatus) {
        assertRole(endpoint, method, "CUSTOMER", customerStatus);
        assertRole(endpoint, method, "FLORIST", vendorStatus);
        assertRole(endpoint, method, "ADMIN", adminStatus);
    }

    /**
     * Asserts that an unauthenticated request receives the expected status.
     */
    public void assertUnauthenticated(String endpoint, HttpMethod method, int expectedStatus) {
        MockHttpServletRequestBuilder builder = request(method, endpoint)
                .header("Authorization", "Bearer invalid-token");
        try {
            mockMvc.perform(builder)
                    .andExpect(status().is(expectedStatus));
        } catch (Exception e) {
            Assertions.fail(
                    "Unauthenticated request to %s %s expected %d but failed: %s".formatted(method, endpoint, expectedStatus, e.getMessage())
            );
        }
    }

    private void assertRole(String endpoint, HttpMethod method, String role, int expectedStatus) {
        MockHttpServletRequestBuilder builder = request(method, endpoint)
                .with(user("test-" + role.toLowerCase() + "@test.com").roles(role));
        try {
            mockMvc.perform(builder)
                    .andExpect(status().is(expectedStatus));
        } catch (Exception e) {
            Assertions.fail(
                    "Role %s on %s %s expected %d but failed: %s".formatted(role, method, endpoint, expectedStatus, e.getMessage())
            );
        }
    }
}
