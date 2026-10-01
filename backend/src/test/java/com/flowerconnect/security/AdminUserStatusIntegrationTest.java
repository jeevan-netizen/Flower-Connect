package com.flowerconnect.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.RefreshTokenRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for plan task 2.8 against real MySQL: the admin user status
 * API, the user listing, the RBAC boundary, session revocation and the audit
 * trail.
 *
 * <p>The MySQL container is shared by the whole integration run, so every
 * assertion is scoped to rows this test created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserStatusIntegrationTest extends AbstractIntegrationTest {

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
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;
    private String floristToken;
    private Long adminId;

    @BeforeEach
    void setUp() throws Exception {
        User admin = createUser(uniqueEmail(), "ADMIN");
        adminId = admin.getId();
        adminToken = tokenFor(admin);
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
        floristToken = tokenFor(createUser(uniqueEmail(), "FLORIST"));
    }

    // ================================================================ RBAC matrix

    @Test
    void theStatusRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theListingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerCannotChangeAUsersStatus() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isForbidden());

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
    }

    @Test
    void aVendorCannotChangeAUsersStatus() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(floristToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isForbidden());

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
    }

    @Test
    void aCustomerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aVendorCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(floristToken)))
                .andExpect(status().isForbidden());
    }

    // ============================================================= status changes

    @Test
    void anAdminCanSuspendACustomer() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Payment fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().intValue()))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        assertEquals(User.Status.SUSPENDED, reload(target).getStatus());
    }

    @Test
    void anAdminCanReactivateASuspendedCustomer() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(target, User.Status.SUSPENDED, "abuse");
        changeStatus(target, User.Status.ACTIVE, "appeal upheld");

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
    }

    @Test
    void anAdminCanDisableAnActiveCustomer() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        changeStatus(target, User.Status.DISABLED, "fraud");

        assertEquals(User.Status.DISABLED, reload(target).getStatus());
    }

    @Test
    void anAdminCanDisableASuspendedCustomer() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(target, User.Status.SUSPENDED, "under review");

        changeStatus(target, User.Status.DISABLED, "confirmed fraud");

        assertEquals(User.Status.DISABLED, reload(target).getStatus());
    }

    @Test
    void anAdminCanSuspendAVendor() throws Exception {
        User vendor = createUser(uniqueEmail(), "FLORIST");

        changeStatus(vendor, User.Status.SUSPENDED, "unresolved complaints");

        assertEquals(User.Status.SUSPENDED, reload(vendor).getStatus());
    }

    @Test
    void anAdminMayChangeAnotherAdminsStatus() throws Exception {
        User peerAdmin = createUser(uniqueEmail(), "ADMIN");

        changeStatus(peerAdmin, User.Status.SUSPENDED, "peer review");

        assertEquals(User.Status.SUSPENDED, reload(peerAdmin).getStatus());
        assertEquals(User.Status.ACTIVE, reload(userRepository.findById(adminId).orElseThrow()).getStatus(),
                "the acting admin is untouched");
    }

    @Test
    void anAdminCannotChangeTheirOwnStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + adminId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"self\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        UserStatusService.MESSAGE_SELF_STATUS_CHANGE));

        assertEquals(User.Status.ACTIVE, reload(userRepository.findById(adminId).orElseThrow()).getStatus());
        assertEquals(0, auditRows(adminId).size(),
                "a refused self-change must not leave an audit row");
    }

    @Test
    void anUnknownUserIsNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/99999999/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aMissingReasonIsRejectedAndNothingChanges() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
        assertEquals(0, auditRows(target.getId()).size());
    }

    @Test
    void aBlankReasonIsRejectedAndNothingChanges() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
        assertEquals(0, auditRows(target.getId()).size());
    }

    @Test
    void anUnknownStatusIsRejectedAndNothingChanges() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(User.Status.ACTIVE, reload(target).getStatus());
        assertEquals(0, auditRows(target.getId()).size());
    }

    // ============================================================ session revocation

    @Test
    void suspensionRevokesEveryRefreshTokenTheUserHolds() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        // Two independent sessions for the same user.
        String firstSession = tokenFor(target);
        assertNotNull(firstSession);
        String secondSession = tokenFor(target);
        assertNotNull(secondSession);

        assertEquals(2, refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(target.getId()).size());

        changeStatus(target, User.Status.SUSPENDED, "fraud");

        assertEquals(0, refreshTokenRepository
                        .findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(target.getId()).size(),
                "every refresh token of the suspended user must be revoked");
    }

    @Test
    void disablingRevokesEveryRefreshTokenTheUserHolds() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        tokenFor(target);

        changeStatus(target, User.Status.DISABLED, "fraud");

        assertEquals(0, refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(target.getId()).size());
    }

    @Test
    void revokingOneUsersSessionsLeavesOtherUsersAlone() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        User bystander = createUser(uniqueEmail(), "CUSTOMER");
        tokenFor(target);
        tokenFor(bystander);

        changeStatus(target, User.Status.SUSPENDED, "fraud");

        assertEquals(1, refreshTokenRepository
                        .findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(bystander.getId()).size(),
                "another user's session must survive");
    }

    /**
     * A revoked token is rejected by {@code validateAndReturnUser} before the
     * status is ever consulted, so the client sees 401 UNAUTHORIZED rather than
     * 403 ACCOUNT_SUSPENDED. Either way the session cannot be renewed.
     */
    @Test
    void aSuspendedUserCannotRefreshAnExistingSession() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        tokenFor(target);
        String refreshToken = refreshTokenFromLogin(target);

        changeStatus(target, User.Status.SUSPENDED, "fraud");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                com.flowerconnect.security.dto.RefreshRequest.builder()
                                        .refreshToken(refreshToken)
                                        .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void aDisabledUserCannotLogInAgain() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        changeStatus(target, User.Status.DISABLED, "fraud");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(target.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void aSuspendedUserCannotUseAnAlreadyIssuedAccessToken() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        String accessToken = tokenFor(target);

        changeStatus(target, User.Status.SUSPENDED, "fraud");

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    @Test
    void aReactivatedUserCanLogInAgain() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(target, User.Status.SUSPENDED, "under review");
        changeStatus(target, User.Status.ACTIVE, "appeal upheld");

        assertNotNull(tokenFor(target));
    }

    // ==================================================================== audit

    @Test
    void everySuccessfulStatusChangeWritesExactlyOneAuditRow() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        changeStatus(target, User.Status.SUSPENDED, "Payment fraud");

        var entries = auditRows(target.getId());
        assertEquals(1, entries.size());
        assertEquals(UserStatusService.ACTION_SUSPENDED, entries.get(0).getActionType());
        assertEquals(UserStatusService.ENTITY_TYPE, entries.get(0).getEntityType());
        assertEquals(target.getId(), entries.get(0).getEntityId());
        assertEquals("Payment fraud", entries.get(0).getReason());
        assertEquals(adminId, entries.get(0).getActor().getId(),
                "the audit row must name the acting admin");
        assertNotNull(entries.get(0).getCreatedAt());
    }

    @Test
    void theStatusLifecycleIsAuditedWithDistinctActionTypes() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");

        changeStatus(target, User.Status.SUSPENDED, "under review");
        changeStatus(target, User.Status.DISABLED, "confirmed fraud");
        changeStatus(target, User.Status.ACTIVE, "appeal upheld");

        var entries = auditRows(target.getId());
        assertEquals(3, entries.size());
        assertEquals(UserStatusService.ACTION_SUSPENDED, entries.get(0).getActionType());
        assertEquals(UserStatusService.ACTION_DISABLED, entries.get(1).getActionType());
        assertEquals(UserStatusService.ACTION_REACTIVATED, entries.get(2).getActionType());
        assertEquals("appeal upheld", entries.get(2).getReason());
    }

    @Test
    void aSuspendedUserCanStillBeAdministeredSoTheAuditTrailGrows() throws Exception {
        User target = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(target, User.Status.SUSPENDED, "one");

        changeStatus(target, User.Status.ACTIVE, "two");

        assertEquals(2, auditRows(target.getId()).size());
    }

    // ================================================================== listing

    @Test
    void anAdminCanListUsers() throws Exception {
        User created = createUser(uniqueEmail(), "CUSTOMER");

        String body = lastPage("");

        assertTrue(body.contains("\"email\":\"" + created.getEmail() + "\""),
                "a newly created user must appear on the newest page");
        assertTrue(body.contains("\"role\":\"CUSTOMER\""));
        assertTrue(body.contains("\"status\":\"ACTIVE\""));
    }

    @Test
    void theListingNeverSerialisesCredentialMaterial() throws Exception {
        User created = createUser(uniqueEmail(), "CUSTOMER");

        var content = new ObjectMapper().readTree(lastPage("role=CUSTOMER")).get("content");
        com.fasterxml.jackson.databind.JsonNode entry = null;
        for (com.fasterxml.jackson.databind.JsonNode candidate : content) {
            if (candidate.get("id").asLong() == created.getId()) {
                entry = candidate;
            }
        }
        assertNotNull(entry, "the created user must be present");
        assertEquals(created.getEmail(), entry.get("email").asText());
        assertFalse(entry.has("passwordHash"), "no password hash may be serialised");
        assertFalse(entry.has("password"), "no credential field may be serialised");
        assertFalse(entry.has("tokenHash"), "no refresh-token hash may be serialised");
        assertFalse(entry.has("refreshToken"), "no refresh token may be serialised");
        assertEquals(7, entry.size(), "only the declared admin-visible fields may be present");
    }

    @Test
    void theListingCanBeFilteredByRole() throws Exception {
        User customer = createUser(uniqueEmail(), "CUSTOMER");
        User florist = createUser(uniqueEmail(), "FLORIST");

        java.util.List<Long> floristIds = listedIds("role=FLORIST");

        assertTrue(floristIds.contains(florist.getId()),
                "the vendor must appear under the FLORIST filter");
        assertFalse(floristIds.contains(customer.getId()),
                "the customer must not appear under the FLORIST filter");
    }

    @Test
    void theListingCanBeFilteredByStatus() throws Exception {
        User suspended = createUser(uniqueEmail(), "CUSTOMER");
        User active = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(suspended, User.Status.SUSPENDED, "fraud");

        java.util.List<Long> suspendedIds = listedIds("status=SUSPENDED");

        assertTrue(suspendedIds.contains(suspended.getId()));
        assertFalse(suspendedIds.contains(active.getId()),
                "an ACTIVE user must not appear under the SUSPENDED filter");
    }

    @Test
    void roleAndStatusFiltersCombine() throws Exception {
        User florist = createUser(uniqueEmail(), "FLORIST");
        changeStatus(florist, User.Status.SUSPENDED, "complaints");
        User suspendedCustomer = createUser(uniqueEmail(), "CUSTOMER");
        changeStatus(suspendedCustomer, User.Status.SUSPENDED, "fraud");
        User activeFlorist = createUser(uniqueEmail(), "FLORIST");

        java.util.List<Long> ids = listedIds("role=FLORIST&status=SUSPENDED");

        assertTrue(ids.contains(florist.getId()));
        assertFalse(ids.contains(suspendedCustomer.getId()),
                "a suspended customer must not appear under role=FLORIST");
        assertFalse(ids.contains(activeFlorist.getId()),
                "an active florist must not appear under status=SUSPENDED");
    }

    @Test
    void theListingIsPaginated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("size", "1")
                        .param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());

        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.first").value(false));
    }

    @Test
    void anUnknownRoleFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("role", "GHOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown role: GHOST"));
    }

    @Test
    void anUnknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aFilterMatchingNothingReturnsAnEmptyPageNotAnError() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("role", "FLORIST")
                        .param("status", "DISABLED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.empty").value(true));
    }

    // =================================================================== helpers

    private void changeStatus(User target, User.Status status, String reason) throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/" + target.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                com.flowerconnect.security.dto.UserStatusUpdateRequest.builder()
                                        .status(status)
                                        .reason(reason)
                                        .build())))
                .andExpect(status().isOk());
    }

    /**
     * Fetches the final page of the listing for the given extra query string.
     * The listing is sorted by {@code createdAt} ascending, so a row this test
     * just created is on the last page; the shared MySQL container accumulates
     * users from every integration test, so page 0 cannot be relied on.
     */
    /**
     * Returns the body of the final page of the listing for the given extra query
     * string. The listing sorts by {@code createdAt} ascending, so a row this test
 * *just* created is on the last page — the shared MySQL container accumulates
     * users from every integration test, so page 0 cannot be relied on.
     *
     * <p>Used only where the filter is narrow enough that the test's own rows
     * dominate the result; the filter tests use {@link #listedIds} instead.
     */
    private String lastPage(String extraQuery) throws Exception {
        int size = 100;
        int totalPages = readInt(fetchPage(size, 0, extraQuery), "totalPages");
        return fetchPage(size, Math.max(0, totalPages - 1), extraQuery);
    }

    /**
     * Walks every page of the listing and returns the ids it contained. Page
     * traversal (rather than a fixed page) keeps the assertion independent of how
     * many rows other integration tests have left in the shared container.
     */
    private java.util.List<Long> listedIds(String extraQuery) throws Exception {
        int size = 100;
        int totalPages = readInt(fetchPage(size, 0, extraQuery), "totalPages");
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int page = 0; page < totalPages; page++) {
            String body = fetchPage(size, page, extraQuery);
            for (com.fasterxml.jackson.databind.JsonNode node :
                    new ObjectMapper().readTree(body).get("content")) {
                ids.add(node.get("id").asLong());
            }
        }
        return ids;
    }

    private String fetchPage(int size, int page, String extraQuery) throws Exception {
        var request = get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .param("size", String.valueOf(size))
                .param("page", String.valueOf(page));
        for (String pair : extraQuery.split("&")) {
            if (!pair.isBlank()) {
                String[] kv = pair.split("=", 2);
                request = request.param(kv[0], kv.length > 1 ? kv[1] : "");
            }
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static int readInt(String json, String field) throws Exception {
        return new ObjectMapper().readTree(json).get(field).asInt();
    }

    private java.util.List<com.flowerconnect.domain.AuditLog> auditRows(Long userId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(
                UserStatusService.ENTITY_TYPE, userId);
    }

    private User reload(User user) {
        return userRepository.findByIdWithRole(user.getId()).orElseThrow();
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
        return login(user).getAccessToken();
    }

    private String refreshTokenFromLogin(User user) throws Exception {
        return login(user).getRefreshToken();
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
        return "adminstatus-" + UUID.randomUUID() + "@test.com";
    }

    /**
     * Ten random decimal digits. {@link UUID#randomUUID()} cannot be used for the
     * suffix because its hex output contains letters, which the registration
     * phone pattern rejects.
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