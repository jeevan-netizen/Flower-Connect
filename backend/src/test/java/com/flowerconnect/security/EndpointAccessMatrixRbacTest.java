package com.flowerconnect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Live security-filter-chain exercise for the reviewed access matrix
 * (plan task 13.6, step 3).
 *
 * <p>For every {@link EndpointAccessMatrix.Entry}, this test issues a request
 * through the <b>real</b> Spring Security filter chain as each of:
 * anonymous, {@code CUSTOMER}, {@code FLORIST} and {@code ADMIN}, and asserts
 * the outcome matches the matrix.
 *
 * <p>Deny cases (401 unauthenticated, 403 wrong role) are asserted exactly --
 * they are decided by the URL layer before any controller runs, so they need
 * no fixtures. Allow cases are asserted as "not 401" (the authenticated caller
 * passes URL-layer access control); the controller may still return 2xx/400/
 * 404/403(ownership) based on business logic and fixtures, which is outside
 * the scope of this access sweep.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAccessMatrixRbacTest extends AbstractIntegrationTest {

    private enum RefRole {
        ANONYMOUS, CUSTOMER, FLORIST, ADMIN
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private String floristToken;
    private String adminToken;

    @Test
    void accessMatrixMatchesRealFilterChainForAllRoles() throws Exception {
        seedUsersAndTokens();

        List<String> failures = new ArrayList<>();
        for (EndpointAccessMatrix.Entry entry : EndpointAccessMatrix.ENTRIES) {
            HttpMethod method = entry.method() == null ? HttpMethod.GET : entry.method();
            String path = entry.testPath();

            check(failures, entry, path, method, null, RefRole.ANONYMOUS);
            check(failures, entry, path, method, customerToken, RefRole.CUSTOMER);
            check(failures, entry, path, method, floristToken, RefRole.FLORIST);
            check(failures, entry, path, method, adminToken, RefRole.ADMIN);
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(
                    "Access matrix does not match the real filter chain:\n"
                            + String.join("\n", failures));
        }
    }

    private void check(List<String> failures,
                       EndpointAccessMatrix.Entry entry,
                       String path, HttpMethod method,
                       String token, RefRole role) throws Exception {
        if (isInfra(path)) {
            // Infra docs/actuator endpoints: springdoc/actuator behaviour in the
            // test profile is not part of the business access contract; just
            // assert they do not error out.
            int actual = status(method, path, token);
            if (actual == 500) {
                failures.add(format(entry, role, method, path, "not-500", actual));
            }
            return;
        }
        Integer expected = expectedStatus(entry, role);
        int actual = status(method, path, token);

        if (expected != null) {
            if (actual != expected) {
                failures.add(format(entry, role, method, path, expected, actual));
            }
        } else {
            // Allow case: the authenticated caller must pass URL-layer access
            // control (not 401). 403 from controller ownership, 404, 400, 2xx
            // are all acceptable here.
            if (actual == 401) {
                failures.add(format(entry, role, method, path, "not-401", actual));
            }
        }
    }

    /** Exact expected status, or null meaning "allow (not 401)". */
    private static Integer expectedStatus(EndpointAccessMatrix.Entry entry, RefRole role) {
        switch (entry.rule()) {
            case PUBLIC:
                return null; // public: no 401 for anyone
            case SIGNATURE:
                return 401; // webhook needs HMAC signature, never a bare JWT
            case ANY_AUTH:
                return role == RefRole.ANONYMOUS ? 401 : null;
            case MULTI_ROLE:
                return role == RefRole.ANONYMOUS ? 401 : null; // CUSTOMER/FLORIST/ADMIN all allowed
            case SIMULATOR_NONPROD:
                if (role == RefRole.ANONYMOUS) return 401;
                if (role == RefRole.CUSTOMER) return null;
                return 403; // FLORIST / ADMIN
            case CUSTOMER:
                if (role == RefRole.ANONYMOUS) return 401;
                if (role == RefRole.CUSTOMER) return null;
                return 403;
            case FLORIST:
                if (role == RefRole.ANONYMOUS) return 401;
                if (role == RefRole.FLORIST) return null;
                return 403;
            case ADMIN:
                if (role == RefRole.ANONYMOUS) return 401;
                if (role == RefRole.ADMIN) return null;
                return 403;
            default:
                throw new IllegalStateException("Unknown rule " + entry.rule());
        }
    }

    private int status(HttpMethod method, String path, String token) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder;
        if (method == HttpMethod.POST) {
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
        } else if (method == HttpMethod.PUT) {
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path)
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
        } else if (method == HttpMethod.PATCH) {
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path)
                    .contentType(MediaType.APPLICATION_JSON).content("{}");
        } else if (method == HttpMethod.DELETE) {
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(path);
        } else {
            builder = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path);
        }
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mockMvc.perform(builder).andReturn();
        return result.getResponse().getStatus();
    }

    private static String format(EndpointAccessMatrix.Entry entry, RefRole role,
                                 HttpMethod method, String path,
                                 Object expected, int actual) {
        return String.format(Locale.ROOT,
                "  %-6s %-45s %-18s role=%-11s expected=%s actual=%d",
                entry.rule(), entry.pathPattern(), method, role, expected, actual);
    }

    private static boolean isInfra(String path) {
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private void seedUsersAndTokens() throws Exception {
        String password = "password123";
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        Role floristRole = roleRepository.findByName("FLORIST").orElseThrow();
        Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        User customer = userRepository.saveAndFlush(User.builder()
                .email("rbac-customer-" + java.util.UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Rbac Customer")
                .phone("+91" + uniqueDigits())
                .role(customerRole).status(User.Status.ACTIVE).build());
        User florist = userRepository.saveAndFlush(User.builder()
                .email("rbac-florist-" + java.util.UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Rbac Florist")
                .phone("+91" + uniqueDigits())
                .role(floristRole).status(User.Status.ACTIVE).build());
        User admin = userRepository.saveAndFlush(User.builder()
                .email("rbac-admin-" + java.util.UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Rbac Admin")
                .phone("+91" + uniqueDigits())
                .role(adminRole).status(User.Status.ACTIVE).build());

        customerToken = login(customer.getEmail(), password).getAccessToken();
        floristToken = login(florist.getEmail(), password).getAccessToken();
        adminToken = login(admin.getEmail(), password).getAccessToken();
    }

    private AuthResponse login(String email, String password) throws Exception {
        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        MvcResult result = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private static String uniqueDigits() {
        StringBuilder d = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 10; i++) d.append(random.nextInt(10));
        return d.toString();
    }
}
