package com.flowerconnect.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for plan task 4.1 against real MySQL: the address-book
 * API, the RBAC boundary, ownership scoping and the default-address rules.
 *
 * <p>The MySQL container is shared by the whole integration run, so every
 * assertion is scoped to the rows this test created — each test signs in its
 * own customer, whose address book starts empty.
 *
 * <p>The RBAC cells here use real JWTs minted through the login endpoint, so
 * the whole production filter chain (namespace rule in {@code SecurityConfig})
 * runs, not just the controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AddressApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private String floristToken;
    private String adminToken;
    private ServiceLocation firstLocation;
    private ServiceLocation secondLocation;

    @BeforeEach
    void setUp() throws Exception {
        // The seeded demo locations, in id order, so a test can change the
        // service location and observe the centroid being recopied.
        List<ServiceLocation> locations = serviceLocationRepository.findAll(Sort.by("id"));
        firstLocation = locations.get(0);
        secondLocation = locations.get(1);

        customerToken = tokenFor(createUser("CUSTOMER"));
        floristToken = tokenFor(createUser("FLORIST"));
        adminToken = tokenFor(createUser("ADMIN"));
    }

    // ================================================================ RBAC matrix

    @Test
    void anUnauthenticatedRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Home", "1 Example Street", null,
                                firstLocation.getId(), null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFloristAccountCannotReachTheAddressBook() throws Exception {
        mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(floristToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(floristToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Home", "1 Example Street", null,
                                firstLocation.getId(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminAccountCannotReachTheAddressBook() throws Exception {
        mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/addresses/1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Home", "1 Example Street", null,
                                firstLocation.getId(), null)))
                .andExpect(status().isForbidden());
    }

    // ================================================================ Create

    @Test
    void theFirstAddressBecomesTheDefaultWhateverTheRequestSays() throws Exception {
        JsonNode created = createAddress(customerToken, "Home", "10 Rose Street",
                "Apt 4B", firstLocation.getId(), false);

        // A customer with one address must be able to receive at it, so an
        // explicit false on the first address is not honoured.
        assertTrue(created.get("defaultAddress").asBoolean());
        assertEquals("Home", created.get("label").asText());
        assertEquals("10 Rose Street", created.get("line1").asText());
        assertEquals("Apt 4B", created.get("line2").asText());
        assertEquals(firstLocation.getId(), created.get("serviceLocationId").asLong());
        // The location is flattened into the read model.
        assertEquals(firstLocation.getCity(), created.get("city").asText());
        assertEquals(firstLocation.getArea(), created.get("area").asText());
        assertEquals(firstLocation.getPincode(), created.get("pincode").asText());
        // Coordinates are the server-copied centroid, never client input.
        assertThat(created.get("latitude").decimalValue())
                .isEqualByComparingTo(firstLocation.getLatitude());
        assertThat(created.get("longitude").decimalValue())
                .isEqualByComparingTo(firstLocation.getLongitude());
    }

    @Test
    void anExplicitDefaultOnALaterAddressClearsThePreviousDefault() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, secondLocation.getId(), true);

        assertTrue(work.get("defaultAddress").asBoolean());
        JsonNode reloadedHome = getAddress(customerToken, home.get("id").asLong());
        assertFalse(reloadedHome.get("defaultAddress").asBoolean());
    }

    @Test
    void aLaterAddressWithoutExplicitDefaultStaysNonDefault() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, secondLocation.getId(), null);

        assertFalse(work.get("defaultAddress").asBoolean());
        assertTrue(getAddress(customerToken, home.get("id").asLong())
                .get("defaultAddress").asBoolean());
    }

    @Test
    void anUnknownServiceLocationIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Home", "1 Rose Street", null, 999999L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void aBlankLabelIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("", "1 Rose Street", null,
                                firstLocation.getId(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.label").exists());
    }

    // ================================================================ List

    @Test
    void listsTheCallersAddressesDefaultFirstThenIdAscending() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode other = createAddress(customerToken, "Other", "3 Rose Street",
                null, firstLocation.getId(), true);

        JsonNode page = objectMapper.readTree(listAddresses(customerToken, 0, 10));
        assertEquals(3L, page.get("totalElements").asLong());

        List<Long> ids = idsOf(page);
        // The explicit default first, then the rest by ascending id.
        assertEquals(List.of(other.get("id").asLong(),
                home.get("id").asLong(), work.get("id").asLong()), ids);

        assertTrue(page.get("content").get(0).get("defaultAddress").asBoolean());
        assertFalse(page.get("content").get(1).get("defaultAddress").asBoolean());
        assertFalse(page.get("content").get(2).get("defaultAddress").asBoolean());
    }

    @Test
    void listingIsPaginatedAndReportsTotals() throws Exception {
        JsonNode first = createAddress(customerToken, "First", "1 Rose Street",
                null, firstLocation.getId(), null);
        createAddress(customerToken, "Second", "2 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode third = createAddress(customerToken, "Third", "3 Rose Street",
                null, firstLocation.getId(), null);

        JsonNode pageZero = objectMapper.readTree(listAddresses(customerToken, 0, 2));
        assertEquals(2, pageZero.get("content").size());
        assertEquals(3L, pageZero.get("totalElements").asLong());
        assertEquals(2, pageZero.get("totalPages").asInt());
        assertEquals(0, pageZero.get("page").asInt());
        assertEquals(2, pageZero.get("size").asInt());
        assertTrue(pageZero.get("first").asBoolean());
        assertFalse(pageZero.get("last").asBoolean());
        assertFalse(pageZero.get("empty").asBoolean());
        assertEquals(first.get("id").asLong(),
                pageZero.get("content").get(0).get("id").asLong());

        JsonNode pageOne = objectMapper.readTree(listAddresses(customerToken, 1, 2));
        assertEquals(1, pageOne.get("content").size());
        assertTrue(pageOne.get("last").asBoolean());
        assertFalse(pageOne.get("first").asBoolean());
        assertEquals(third.get("id").asLong(),
                pageOne.get("content").get(0).get("id").asLong());
    }

    @Test
    void invalidPaginationParametersAreRefused() throws Exception {
        mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .param("size", "1000"))
                .andExpect(status().isBadRequest());
    }

    // ================================================================ Read

    @Test
    void readsOneOfTheCallersAddresses() throws Exception {
        JsonNode created = createAddress(customerToken, "Home", "10 Rose Street",
                "Apt 4B", firstLocation.getId(), null);

        mockMvc.perform(get("/api/v1/addresses/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Home"))
                .andExpect(jsonPath("$.line1").value("10 Rose Street"))
                .andExpect(jsonPath("$.line2").value("Apt 4B"))
                .andExpect(jsonPath("$.serviceLocationId").value(firstLocation.getId().intValue()))
                .andExpect(jsonPath("$.city").value(firstLocation.getCity()))
                .andExpect(jsonPath("$.area").value(firstLocation.getArea()))
                .andExpect(jsonPath("$.pincode").value(firstLocation.getPincode()))
                .andExpect(jsonPath("$.defaultAddress").value(true))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void aMissingAddressIs404() throws Exception {
        mockMvc.perform(get("/api/v1/addresses/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void anotherCustomersAddressIs404() throws Exception {
        JsonNode mine = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        String strangerToken = tokenFor(createUser("CUSTOMER"));
        Long id = mine.get("id").asLong();

        // A foreign id and a missing id are the same 404: the caller cannot
        // distinguish "someone else's" from "nonexistent".
        mockMvc.perform(get("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Stolen", "9 Rose Street", null,
                                firstLocation.getId(), null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(strangerToken)))
                .andExpect(status().isNotFound());

        // The stranger's attempt changed nothing.
        assertEquals("Home", getAddress(customerToken, id).get("label").asText());
    }

    @Test
    void aNonPositiveAddressIdIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/addresses/0")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ================================================================ Update

    @Test
    void updateIsAFullReplacementThatKeepsTheStoredDefault() throws Exception {
        JsonNode created = createAddress(customerToken, "Home", "10 Rose Street",
                "Apt 4B", firstLocation.getId(), null);
        Long id = created.get("id").asLong();

        // line2 and defaultAddress omitted: a full replacement clears line2 and
        // keeps the stored default flag.
        JsonNode updated = updateAddress(customerToken, id, "Home (renamed)",
                "20 Rose Street", null, secondLocation.getId(), null);

        assertEquals("Home (renamed)", updated.get("label").asText());
        assertEquals("20 Rose Street", updated.get("line1").asText());
        assertTrue(updated.get("line2").isNull());
        assertTrue(updated.get("defaultAddress").asBoolean());
        assertEquals(secondLocation.getId(), updated.get("serviceLocationId").asLong());
        assertThat(updated.get("latitude").decimalValue())
                .isEqualByComparingTo(secondLocation.getLatitude());
        assertThat(updated.get("longitude").decimalValue())
                .isEqualByComparingTo(secondLocation.getLongitude());
    }

    @Test
    void updateRecopiesTheCentroidWhenTheServiceLocationChanges() throws Exception {
        JsonNode created = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        Long id = created.get("id").asLong();

        JsonNode updated = updateAddress(customerToken, id, "Home", "1 Rose Street",
                null, secondLocation.getId(), null);

        assertEquals(secondLocation.getId(), updated.get("serviceLocationId").asLong());
        assertThat(updated.get("latitude").decimalValue())
                .isEqualByComparingTo(secondLocation.getLatitude());
        assertThat(updated.get("longitude").decimalValue())
                .isEqualByComparingTo(secondLocation.getLongitude());
    }

    @Test
    void updateSettingAnotherDefaultClearsThePreviousDefault() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, firstLocation.getId(), null);

        JsonNode updated = updateAddress(customerToken, work.get("id").asLong(),
                "Work", "2 Rose Street", null, firstLocation.getId(), true);

        assertTrue(updated.get("defaultAddress").asBoolean());
        assertFalse(getAddress(customerToken, home.get("id").asLong())
                .get("defaultAddress").asBoolean());
    }

    @Test
    void updateExplicitlyClearingTheDefaultLeavesNoDefault() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);

        JsonNode updated = updateAddress(customerToken, home.get("id").asLong(),
                "Home", "1 Rose Street", null, firstLocation.getId(), false);

        assertFalse(updated.get("defaultAddress").asBoolean());
        // Demotion is not a delete: the book is not empty, but nothing is
        // the default until one is set explicitly or the address is deleted.
        JsonNode page = objectMapper.readTree(listAddresses(customerToken, 0, 10));
        assertEquals(1L, page.get("totalElements").asLong());
        for (JsonNode node : page.get("content")) {
            assertFalse(node.get("defaultAddress").asBoolean());
        }
    }

    @Test
    void updatingWithAnUnknownServiceLocationIs400() throws Exception {
        JsonNode created = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);

        mockMvc.perform(put("/api/v1/addresses/" + created.get("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Home", "1 Rose Street", null, 999999L, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    // ================================================================ Delete

    @Test
    void deletingTheDefaultPromotesTheOldestRemainingAddress() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode other = createAddress(customerToken, "Other", "3 Rose Street",
                null, firstLocation.getId(), null);

        deleteAddress(customerToken, home.get("id").asLong());

        // The oldest remaining address (lowest id) becomes the default.
        assertTrue(getAddress(customerToken, work.get("id").asLong())
                .get("defaultAddress").asBoolean());
        assertFalse(getAddress(customerToken, other.get("id").asLong())
                .get("defaultAddress").asBoolean());
    }

    @Test
    void deletingANonDefaultAddressLeavesTheDefaultAlone() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);
        JsonNode work = createAddress(customerToken, "Work", "2 Rose Street",
                null, firstLocation.getId(), null);

        deleteAddress(customerToken, work.get("id").asLong());

        assertTrue(getAddress(customerToken, home.get("id").asLong())
                .get("defaultAddress").asBoolean());
    }

    @Test
    void deletingTheOnlyAddressLeavesTheBookEmpty() throws Exception {
        JsonNode home = createAddress(customerToken, "Home", "1 Rose Street",
                null, firstLocation.getId(), null);

        deleteAddress(customerToken, home.get("id").asLong());

        JsonNode page = objectMapper.readTree(listAddresses(customerToken, 0, 10));
        assertEquals(0L, page.get("totalElements").asLong());
        assertTrue(page.get("empty").asBoolean());
    }

    // ================================================================ Helpers

    private JsonNode createAddress(String token, String label, String line1,
            String line2, Long serviceLocationId, Boolean defaultAddress) throws Exception {
        String body = mockMvc.perform(post("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(label, line1, line2, serviceLocationId, defaultAddress)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode updateAddress(String token, Long id, String label, String line1,
            String line2, Long serviceLocationId, Boolean defaultAddress) throws Exception {
        String body = mockMvc.perform(put("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(label, line1, line2, serviceLocationId, defaultAddress)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void deleteAddress(String token, Long id) throws Exception {
        mockMvc.perform(delete("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
    }

    private JsonNode getAddress(String token, Long id) throws Exception {
        String body = mockMvc.perform(get("/api/v1/addresses/" + id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String listAddresses(String token, int page, int size) throws Exception {
        return mockMvc.perform(get("/api/v1/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private List<Long> idsOf(JsonNode page) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : page.get("content")) {
            ids.add(node.get("id").asLong());
        }
        return ids;
    }

    private String addressJson(String label, String line1, String line2,
            Long serviceLocationId, Boolean defaultAddress) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("label", label);
        body.put("line1", line1);
        if (line2 != null) {
            body.put("line2", line2);
        }
        body.put("serviceLocationId", serviceLocationId);
        if (defaultAddress != null) {
            body.put("defaultAddress", defaultAddress);
        }
        return objectMapper.writeValueAsString(body);
    }

    private User createUser(String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(uniqueEmail())
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Address " + roleName)
                .phone(uniquePhone())
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        return login(user).getAccessToken();
    }

    private AuthResponse login(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private static String uniqueEmail() {
        return "address-api-" + UUID.randomUUID() + "@test.com";
    }

    /**
     * Ten random decimal digits. {@link UUID#randomUUID()} cannot be used for
     * the suffix because its hex output contains letters, which the user phone
     * pattern rejects.
     */
    private static String uniquePhone() {
        java.util.Random random = new java.util.Random();
        StringBuilder digits = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            digits.append(random.nextInt(10));
        }
        return "+91" + digits;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
