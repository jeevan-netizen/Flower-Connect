package com.flowerconnect.vendor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.security.VendorApprovalGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Approval gating against real MySQL (plan task 2.7).
 *
 * <p>{@code TestVendorFeatureController} stands in for the Phase 3 catalog and
 * order endpoints: {@code /guarded} carries {@code @RequiresApprovedVendor},
 * {@code /unguarded} does not. No production catalog route exists yet, and this
 * task deliberately does not add one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "vendor-approval-probe"})
class VendorApprovalGatingIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
    private static final String GUARDED = "/api/v1/vendors/test-features/guarded";
    private static final String UNGUARDED = "/api/v1/vendors/test-features/unguarded";
    private static final String KORAMANGALA_PINCODE = "560034";

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
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = tokenFor(createUser(uniqueEmail(), "ADMIN"));
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ===================================================== RBAC is answered first

    @Test
    void anUnauthenticatedCallerIsUnauthorized() throws Exception {
        mockMvc.perform(get(GUARDED))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerIsForbiddenByTheRoleBoundary() throws Exception {
        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void anAdminIsForbiddenByTheRoleBoundary() throws Exception {
        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ========================================================= status matrix

    @Test
    void aPendingVendorIsRefusedWithAnApprovalSpecificError() throws Exception {
        Vendor vendor = registerVendor();

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"))
                .andExpect(jsonPath("$.message").value(VendorApprovalGuard.MESSAGE_PENDING));
    }

    @Test
    void aRejectedVendorIsRefused() throws Exception {
        Vendor vendor = registerVendor();
        reject(vendor.profileId);

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"))
                .andExpect(jsonPath("$.message").value(VendorApprovalGuard.MESSAGE_REJECTED));
    }

    @Test
    void aSuspendedVendorIsRefused() throws Exception {
        Vendor vendor = registerVendor();
        approve(vendor.profileId);
        suspend(vendor.profileId);

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"))
                .andExpect(jsonPath("$.message").value(VendorApprovalGuard.MESSAGE_SUSPENDED));
    }

    @Test
    void anApprovedVendorIsAllowed() throws Exception {
        Vendor vendor = registerVendor();
        approve(vendor.profileId);

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(vendor.email))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void aVendorAccountWithNoProfileIsRefused() throws Exception {
        User orphan = createUser(uniqueEmail(), "FLORIST");

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(orphan))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"))
                .andExpect(jsonPath("$.message").value(VendorApprovalGuard.MESSAGE_NO_PROFILE));
    }

    // ================================================ status is read per request

    @Test
    void approvalTakesEffectOnTheVeryNextRequest() throws Exception {
        Vendor vendor = registerVendor();

        assertRefused(vendor.token, VendorApprovalGuard.MESSAGE_PENDING);

        approve(vendor.profileId);

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk());
    }

    @Test
    void suspensionTakesEffectOnTheVeryNextRequestWithoutReAuthentication() throws Exception {
        Vendor vendor = registerVendor();
        approve(vendor.profileId);
        String tokenBeforeSuspension = vendor.token;

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(tokenBeforeSuspension)))
                .andExpect(status().isOk());

        suspend(vendor.profileId);

        // Same, still-valid access token: approval state is never carried in the JWT.
        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(tokenBeforeSuspension)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("VENDOR_NOT_APPROVED"));
    }

    @Test
    void reinstatementRestoresAccess() throws Exception {
        Vendor vendor = registerVendor();
        approve(vendor.profileId);
        suspend(vendor.profileId);
        reinstate(vendor.profileId);

        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk());
    }

    // ============================================ ownership comes from the JWT

    @Test
    void theGuardedRouteIgnoresAnyClientSuppliedVendorIdentifier() throws Exception {
        Vendor caller = registerVendor();
        Vendor other = registerVendor();
        approve(other.profileId);
        approve(caller.profileId);

        mockMvc.perform(get(GUARDED)
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller.token))
                        .param("vendorId", String.valueOf(other.profileId))
                        .param("profileId", String.valueOf(other.profileId))
                        .param("userId", String.valueOf(other.profileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(caller.email));
    }

    // ================================== gating is per-handler, not per-namespace

    @Test
    void anUnannotatedRouteInTheSameControllerIsNotGated() throws Exception {
        Vendor vendor = registerVendor();

        mockMvc.perform(get(UNGUARDED).header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    // ============================== self-service routes stay exempt from gating

    @Test
    void aPendingVendorCanStillReadAndUpdateItsOwnProfile() throws Exception {
        Vendor vendor = registerVendor();

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void aSuspendedVendorCanStillReadItsOwnProfile() throws Exception {
        Vendor vendor = registerVendor();
        approve(vendor.profileId);
        suspend(vendor.profileId);

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void aRejectedVendorCanStillReadItsOwnProfile() throws Exception {
        Vendor vendor = registerVendor();
        reject(vendor.profileId);

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ------------------------------------------------------------------- helpers

    private void assertRefused(String token, String expectedMessage) throws Exception {
        mockMvc.perform(get(GUARDED).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    private Vendor registerVendor() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        VendorProfile profile = vendorProfileRepository.findByUserId(user.getId()).orElseThrow();
        assertEquals(VendorProfile.Status.PENDING_APPROVAL, profile.getStatus());
        return new Vendor(email, tokenFor(user), profile.getId());
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private void reject(Long profileId) throws Exception {
        transition(profileId, "reject", VendorProfile.Status.REJECTED);
    }

    private void suspend(Long profileId) throws Exception {
        transition(profileId, "suspend", VendorProfile.Status.SUSPENDED);
    }

    private void transition(Long profileId, String action, VendorProfile.Status expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                VendorAdminReasonRequest.builder().reason("integration test").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus.name()));
    }

    private void reinstate(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/reinstate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private Long koramangalaId() {
        return serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow().getId();
    }

    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Test " + roleName)
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
        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
                .getAccessToken();
    }

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Test Blossoms");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(koramangalaId());
        request.setPrepTimeMinutes(30);
        request.setSlotDurationMinutes(60);
        request.setMaxOrdersPerSlot(10);
        return request;
    }

    private VendorProfileUpdateRequest updateRequest() {
        VendorProfileUpdateRequest request = new VendorProfileUpdateRequest();
        request.setBusinessName("Test Blossoms Renamed");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(koramangalaId());
        return request;
    }

    private static String uniqueEmail() {
        return "vendor-gate-" + UUID.randomUUID() + "@test.com";
    }

    private static String uniquePhone() {
        // Decimal digits only: a UUID contains a-f and would fail the phone pattern.
        return "+91" + String.format("%010d",
                Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 10_000_000_000L));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Vendor(String email, String token, Long profileId) {
    }
}
