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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for plan task 3.1 against real MySQL: the category
 * API contract, RBAC boundary, cycle prevention, delete protection, and seed data.
 *
 * <p>The public and admin read paths are asserted here rather than in a service
 * test because both contracts are enforced by SQL: "active only" lives in the
 * public query's predicate and "every category, correctly counted" lives in
 * which rows the paged query selects. A mocked repository cannot observe either.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    /** The id and slug the API returned for a category this test created. */
    private record CategoryRow(long id, String slug) {
    }

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

    /**
     * Regression test for the Phase 3 audit's HIGH-1: the public read must not
     * serve an inactive category. The inactive rows are created here rather
     * than assumed, and the count is asserted as well as the contents, so the
     * test fails both when an inactive category appears and when the query
     * returns roots without the {@code active} predicate at all.
     */
    @Test
    void publicCategoryReadExcludesInactiveRoots() throws Exception {
        long before = publicCategoryCount();

        CategoryRow activeRoot = createCategory(uniqueName("Public Active Root"), 1, true, null);
        CategoryRow inactiveRoot = createCategory(uniqueName("Public Inactive Root"), 1, false, null);
        // An inactive parent must not be published just because its child is
        // active: the "active" filter is per category, not inherited upwards.
        CategoryRow inactiveParent = createCategory(uniqueName("Public Inactive Parent"), 1, false, null);
        CategoryRow activeChildOfInactive = createCategory(
                uniqueName("Public Active Child"), 1, true, inactiveParent.id());

        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> published = slugsIn(body);
        assertThat(published)
                .contains(activeRoot.slug())
                .doesNotContain(inactiveRoot.slug(), inactiveParent.slug());

        assertThat(readJson(body, "$.totalElements"))
                .as("only the one new active top-level category is published")
                .isEqualTo(before + 1);

        // The active child is not a top-level category, so the public list is
        // unchanged by it; assert it explicitly so the count above is readable.
        assertThat(published).doesNotContain(activeChildOfInactive.slug());
    }

    /**
     * Regression test for the Phase 3 audit's HIGH-2: with no {@code parentId}
     * the admin listing covers every category, not only the roots, and its
     * pagination counts them. Under the roots-only query {@code totalElements}
     * was one short and the child was unreachable without knowing its parent's
     * id first.
     */
    @Test
    void adminListingWithoutParentIdIncludesChildrenAndCountsThem() throws Exception {
        long before = adminCategoryCount();

        CategoryRow root = createCategory(uniqueName("Listing Root"), 1, true, null);
        CategoryRow child = createCategory(uniqueName("Listing Child"), 1, true, root.id());
        CategoryRow inactiveRoot = createCategory(uniqueName("Listing Inactive Root"), 1, false, null);

        String oneRowPerPage = adminListing(0, 1, null);

        assertThat(readJson(oneRowPerPage, "$.totalElements"))
                .as("every category is counted: two roots, one inactive root and one child")
                .isEqualTo(before + 3);
        assertThat(readJson(oneRowPerPage, "$.totalPages"))
                .as("size=1 yields one page per counted category")
                .isEqualTo(before + 3);

        assertThat(listedCategorySlugs())
                .contains(root.slug(), child.slug(), inactiveRoot.slug());
    }

    /**
     * The {@code parentId} filter keeps meaning "the direct children of this
     * category", which the unfiltered listing above no longer guarantees on its
     * own — a grandchild and an unrelated root must not leak into it.
     */
    @Test
    void adminListingWithParentIdReturnsOnlyThatParentsDirectChildren() throws Exception {
        CategoryRow root = createCategory(uniqueName("Parent Root"), 1, true, null);
        CategoryRow child = createCategory(uniqueName("Parent Child"), 1, true, root.id());
        CategoryRow otherChild = createCategory(uniqueName("Parent Other Child"), 2, true, root.id());
        CategoryRow grandchild = createCategory(uniqueName("Parent Grandchild"), 1, true, child.id());
        CategoryRow unrelatedRoot = createCategory(uniqueName("Unrelated Root"), 1, true, null);

        String body = adminListing(0, 20, root.id());

        assertThat(slugsIn(body))
                .containsExactlyInAnyOrder(child.slug(), otherChild.slug());
        assertThat(readJson(body, "$.totalElements")).isEqualTo(2);
        assertThat(readJson(body, "$.totalPages")).isEqualTo(1);
        assertThat(slugsIn(body))
                .doesNotContain(root.slug(), grandchild.slug(), unrelatedRoot.slug());
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

    // ------------------------------------------------------------------ helpers

    private CategoryRow createCategory(String name, int displayOrder, boolean active, Long parentId)
            throws Exception {
        String body = """
                {
                  "name": "%s",
                  "displayOrder": %d,
                  "active": %s,
                  "parentId": %s
                }
                """.formatted(name, displayOrder, active, parentId == null ? "null" : parentId);

        String created = mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return new CategoryRow(((Number) JsonPath.read(created, "$.id")).longValue(),
                JsonPath.read(created, "$.slug").toString());
    }

    /** One admin listing page, authenticated, with an explicit page size. */
    private String adminListing(int page, int size, Long parentId) throws Exception {
        var request = get("/api/v1/admin/categories")
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size))
                .header("Authorization", "Bearer " + adminToken);
        if (parentId != null) {
            request = request.param("parentId", String.valueOf(parentId));
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /**
     * Total across the whole shared table, read with {@code size=1} so the
     * reported count is independent of the requested page size.
     */
    private long adminCategoryCount() throws Exception {
        return readJson(adminListing(0, 1, null), "$.totalElements");
    }

    private long publicCategoryCount() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readJson(body, "$.totalElements");
    }

    /**
     * Walks every admin page so an assertion is about membership of the rows
     * this test created rather than about a shared database's size (see the
     * Phase 2 close-out note on accumulating integration data).
     */
    private List<String> listedCategorySlugs() throws Exception {
        List<String> slugs = new ArrayList<>();
        int totalPages = 1;
        for (int page = 0; page < totalPages; page++) {
            String body = adminListing(page, 1, null);
            totalPages = readJson(body, "$.totalPages");
            slugs.addAll(slugsIn(body));
        }
        return slugs;
    }

    /**
     * Slugs in one response body. Typed rather than inlined because
     * {@code JsonPath.read} is generic, which makes {@code assertThat(...)}
     * ambiguous at the call site.
     */
    private static List<String> slugsIn(String body) {
        List<String> slugs = JsonPath.read(body, "$.content[*].slug");
        return slugs;
    }

    private String uniqueName(String base) {
        return base + " " + System.nanoTime();
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