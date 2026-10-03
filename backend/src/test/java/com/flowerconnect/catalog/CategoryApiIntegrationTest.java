package com.flowerconnect.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for plan task 3.1 against real MySQL: the category
 * API contract, RBAC boundary, cycle prevention, delete protection, and seed data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        User admin = createUser(uniqueEmail(), "ADMIN");
        adminToken = tokenFor(admin);
    }

    @Test
    void shouldReturnAllCategoriesFlat() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].slug").value(hasItem("roses")))
                .andExpect(jsonPath("$.content[*].slug").value(hasItem("bouquets")))
                .andExpect(jsonPath("$.content[*].slug").value(hasItem("arrangements")))
                .andExpect(jsonPath("$.content[*].slug").value(hasItem("occasions")))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(4)));
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanCreateCategory() throws Exception {
        String request = """
                {
                  "name": "Test Category",
                  "displayOrder": 5,
                  "active": true
                }
                """;

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-category"));
    }

    @Test
    void adminCanListCategories() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)));
    }

    @Test
    void adminCanUpdateCategory() throws Exception {
        // First create
        String createRequest = """
                {
                  "name": "To Update",
                  "displayOrder": 1
                }
                """;
        String response = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int createdId = JsonPath.read(response, "$.id");

        // Then update
        String updateRequest = """
                {
                  "name": "Updated Name",
                  "displayOrder": 2
                }
                """;

        mockMvc.perform(put("/api/v1/admin/categories/" + createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("updated-name"));
    }

    @Test
    void adminCanDeleteCategory() throws Exception {
        // Create a category to delete
        String createRequest = """
                {
                  "name": "To Delete",
                  "displayOrder": 1
                }
                """;
        String response = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int createdId = JsonPath.read(response, "$.id");

        // Delete it
        mockMvc.perform(delete("/api/v1/admin/categories/" + createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/v1/admin/categories/" + createdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRefusesWhenHasChildren() throws Exception {
        // Create parent
        String parentRequest = """
                {
                  "name": "Parent Category",
                  "displayOrder": 1
                }
                """;
        String parentResponse = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parentRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int parentId = JsonPath.read(parentResponse, "$.id");

        // Create child
        String childRequest = """
                {
                  "name": "Child Category",
                  "parentId": %d,
                  "displayOrder": 1
                }
                """.formatted(parentId);
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(childRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Try to delete parent - should fail with 409
        mockMvc.perform(delete("/api/v1/admin/categories/" + parentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void cyclePreventionWorks() throws Exception {
        // Create category A
        String aRequest = """
                {
                  "name": "Category A",
                  "displayOrder": 1
                }
                """;
        String aResponse = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int aId = JsonPath.read(aResponse, "$.id");

        // Create category B with parent A
        String bRequest = """
                {
                  "name": "Category B",
                  "parentId": %d,
                  "displayOrder": 1
                }
                """.formatted(aId);
        String bResponse = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int bId = JsonPath.read(bResponse, "$.id");

        // Try to set A's parent to B - should fail with 409
        String cycleRequest = """
                {
                  "name": "Category A",
                  "parentId": %d,
                  "displayOrder": 1
                }
                """.formatted(bId);
        mockMvc.perform(put("/api/v1/admin/categories/" + aId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cycleRequest)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        String customerToken = customerToken();

        mockMvc.perform(get("/api/v1/admin/categories")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Test\"}")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
                .phone(uniquePhone())
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        return auth.getAccessToken();
    }

    private String customerToken() throws Exception {
        User customer = createUser(uniqueEmail(), "CUSTOMER");
        return tokenFor(customer);
    }

    private String uniqueEmail() {
        return "test-" + System.nanoTime() + "@example.com";
    }

    private String uniquePhone() {
        return "+91" + System.nanoTime() % 10000000000L;
    }

    private static int readJson(String json, String pointer) throws Exception {
        return Integer.parseInt(
                JsonPath.read(json, pointer).toString());
    }
}