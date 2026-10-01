package com.flowerconnect.vendor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import com.flowerconnect.vendor.dto.VendorHoursRequest;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.service.VendorAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end coverage for plan tasks 2.5 and 2.6 against real MySQL: the API
 * contract, the JWT/RBAC boundary, cross-vendor isolation, registration
 * atomicity, the audit trail, and the V6 CHECK constraints.
 *
 * <p>The MySQL container is shared by the whole integration run, so assertions
 * never assume an exclusive data set; they scope on the rows this test created.
 */
@SpringBootTest
@AutoConfigureMockMvc
class VendorApiIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";
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
    private VendorHoursRepository vendorHoursRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private String adminToken;
    private Long adminUserId;
    private String customerToken;
    private Long koramangalaId;

    @BeforeEach
    void setUp() throws Exception {
        koramangalaId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
        User admin = createUser(uniqueEmail(), "ADMIN");
        adminUserId = admin.getId();
        adminToken = tokenFor(admin);
        customerToken = tokenFor(createUser(uniqueEmail(), "CUSTOMER"));
    }

    // ================================================================ task 2.5: register

    @Test
    void registrationCreatesAnAccountAndAProfileInPendingApproval() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.businessName").value("Test Blossoms"))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.prepTimeMinutes").value(45))
                .andExpect(jsonPath("$.minOrderAmount").value(199.99))
                .andExpect(jsonPath("$.serviceLocationId").value(koramangalaId.intValue()))
                .andExpect(jsonPath("$.pincode").value(KORAMANGALA_PINCODE))
                .andExpect(jsonPath("$.latitude").isNumber())
                .andExpect(jsonPath("$.longitude").isNumber());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertEquals("FLORIST", saved.getRole().getName(), "vendor account must carry the FLORIST role");
        assertNotEquals(PASSWORD, saved.getPasswordHash(), "password must be hashed");
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));

        VendorProfile profile = vendorProfileRepository.findByUserId(saved.getId()).orElseThrow();
        assertEquals(VendorProfile.Status.PENDING_APPROVAL, profile.getStatus());
    }

    @Test
    void registrationAppliesSchemaDefaultsForOmittedSettings() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setPrepTimeMinutes(null);
        request.setDeliveryRadiusKm(null);
        request.setMinOrderAmount(null);
        request.setBaseDeliveryFee(null);
        request.setPerKmFee(null);
        request.setSlotDurationMinutes(null);
        request.setMaxOrdersPerSlot(null);
        request.setAcceptingOrders(null);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.prepTimeMinutes").value(30))
                .andExpect(jsonPath("$.slotDurationMinutes").value(60))
                .andExpect(jsonPath("$.maxOrdersPerSlot").value(10))
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.deliveryRadiusKm").value(5.00))
                .andExpect(jsonPath("$.minOrderAmount").value(0.00));
    }

    @Test
    void registrationRejectsADuplicateEmail() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registrationRejectsADuplicatePhone() throws Exception {
        String phone = uniquePhone();

        VendorRegisterRequest first = registerRequest(uniqueEmail());
        first.setPhone(phone);
        register(first);

        VendorRegisterRequest second = registerRequest(uniqueEmail());
        second.setPhone(phone);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertFalse(userRepository.existsByEmail(second.getEmail()));
    }

    @Test
    void registrationRejectsANonPositivePrepTime() throws Exception {
        for (Integer prepTime : List.of(0, -5)) {
            VendorRegisterRequest request = registerRequest(uniqueEmail());
            request.setPrepTimeMinutes(prepTime);

            mockMvc.perform(post("/api/v1/vendors/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validation.prepTimeMinutes").exists());

            assertFalse(userRepository.existsByEmail(request.getEmail()),
                    "no account may be created when validation fails");
        }
    }

    @Test
    void registrationRejectsAMissingBusinessName() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setBusinessName(null);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.businessName").exists());
    }

    @Test
    void registrationRejectsAMissingAddressLine() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setAddressLine1("  ");

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.addressLine1").exists());
    }

    @Test
    void registrationRejectsAnUnknownServiceLocation() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setServiceLocationId(9_999_999L);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void registrationLeavesNoOrphanAccountWhenProfileCreationFails() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setServiceLocationId(9_999_999L);
        String businessName = "Rollback Probe " + UUID.randomUUID();
        request.setBusinessName(businessName);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail(request.getEmail()),
                "the account insert must roll back with the failed profile insert");
        assertEquals(0L, countProfilesNamed(businessName),
                "no vendor_profiles row may survive the rolled-back registration");
    }

    @Test
    void registrationLeavesNoOrphanProfileWhenTheWeekIsRejected() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        String businessName = "Hours Rollback Probe " + UUID.randomUUID();
        request.setBusinessName(businessName);
        // validateHours runs before the user insert, so nothing is written at all
        // here; this is the second distinct failure mode of the same invariant.
        request.setHours(List.of(openDay(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(9, 0))));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail(request.getEmail()));
        assertEquals(0L, countProfilesNamed(businessName));
    }

    // ================================================================ task 2.5: hours

    @Test
    void registrationAcceptsAValidOpenWeek() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(
                openDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                VendorHoursRequest.builder().weekday(DayOfWeek.SUNDAY).closed(true).build()));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hours.length()").value(2))
                .andExpect(jsonPath("$.hours[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.hours[0].openTime").value("09:00:00"))
                .andExpect(jsonPath("$.hours[0].closeTime").value("18:00:00"))
                .andExpect(jsonPath("$.hours[1].closed").value(true))
                .andExpect(jsonPath("$.hours[1].openTime").doesNotExist());

        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();
        VendorProfile profile = vendorProfileRepository.findByUserId(user.getId()).orElseThrow();
        List<VendorHours> hours = vendorHoursRepository
                .findByVendorProfileIdOrderByWeekdayAsc(profile.getId());
        assertEquals(2, hours.size());
        assertTrue(hours.stream().anyMatch(h -> h.getWeekday() == DayOfWeek.SUNDAY && h.isClosed()));
    }

    @Test
    void registrationRejectsAClosedDayThatCarriesTimes() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.SUNDAY).closed(true)
                .openTime(LocalTime.of(9, 0)).closeTime(LocalTime.of(18, 0)).build()));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "Opening hours for SUNDAY are marked closed but specify times"));
    }

    @Test
    void registrationRejectsAnOpenDayWithoutAnOpenTime() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.MONDAY).closeTime(LocalTime.of(18, 0)).closed(false).build()));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Opening hours for MONDAY are open but openTime is missing"));
    }

    @Test
    void registrationRejectsAnOpenDayWithoutACloseTime() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.MONDAY).openTime(LocalTime.of(9, 0)).closed(false).build()));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Opening hours for MONDAY are open but closeTime is missing"));
    }

    @Test
    void registrationRejectsACloseTimeThatDoesNotFollowTheOpenTime() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(openDay(DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(9, 0))));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Opening hours for MONDAY must close after they open"));
    }

    @Test
    void registrationRejectsAnEqualOpenAndCloseTime() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(openDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0))));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRejectsADuplicateWeekday() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(
                openDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                openDay(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(19, 0))));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Duplicate weekday in opening hours: MONDAY"));
    }

    @Test
    void registrationLeavesNoOrphanAccountWhenHoursAreInvalid() throws Exception {
        VendorRegisterRequest request = registerRequest(uniqueEmail());
        request.setHours(List.of(openDay(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(9, 0))));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(userRepository.existsByEmail(request.getEmail()));
    }

    // ========================================================== task 2.5: own profile

    @Test
    void aVendorCanReadTheirOwnProfile() throws Exception {
        VendorFixture vendor = registerWithVendor();

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(vendor.profileId().intValue()))
                .andExpect(jsonPath("$.ownerEmail").value(vendor.email()))
                .andExpect(jsonPath("$.businessName").value("Test Blossoms"));
    }

    @Test
    void aVendorCanUpdateTheirOwnProfileAndSettings() throws Exception {
        VendorFixture vendor = registerWithVendor();

        VendorProfileUpdateRequest update = new VendorProfileUpdateRequest();
        update.setBusinessName("Renamed Blossoms");
        update.setDescription("Now with roses");
        update.setAddressLine1("99 New Road");
        update.setServiceLocationId(koramangalaId);
        update.setMinOrderAmount(new BigDecimal("250.00"));
        update.setBaseDeliveryFee(new BigDecimal("30.00"));
        update.setPerKmFee(new BigDecimal("2.50"));
        update.setFreeDeliveryAbove(new BigDecimal("600.00"));
        update.setPrepTimeMinutes(20);
        update.setSlotDurationMinutes(45);
        update.setMaxOrdersPerSlot(5);
        update.setAcceptingOrders(false);
        update.setHours(List.of(openDay(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))));

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Renamed Blossoms"))
                .andExpect(jsonPath("$.minOrderAmount").value(250.00))
                .andExpect(jsonPath("$.prepTimeMinutes").value(20))
                .andExpect(jsonPath("$.acceptingOrders").value(false))
                .andExpect(jsonPath("$.hours.length()").value(1))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        VendorProfile reloaded =
                vendorProfileRepository.findByIdWithDetails(vendor.profileId()).orElseThrow();
        assertEquals("Renamed Blossoms", reloaded.getBusinessName());
        assertEquals(1, vendorHoursRepository
                .findByVendorProfileIdOrderByWeekdayAsc(vendor.profileId()).size());
    }

    @Test
    void anUpdateCannotChangeTheApprovalStatusOrTheReviewCount() throws Exception {
        VendorFixture vendor = registerWithVendor();

        String body = """
                {"businessName":"Sneaky Blossoms","addressLine1":"1 St","serviceLocationId":%d,
                 "status":"APPROVED","reviewCount":999,"avgRating":5.0,"commissionRate":0.5}
                """.formatted(koramangalaId);

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.reviewCount").value(0));

        VendorProfile reloaded =
                vendorProfileRepository.findByIdWithDetails(vendor.profileId()).orElseThrow();
        assertEquals(VendorProfile.Status.PENDING_APPROVAL, reloaded.getStatus());
        assertEquals(0, reloaded.getReviewCount());
        assertNull(reloaded.getAvgRating());
        assertNull(reloaded.getCommissionRate());
    }

    @Test
    void anUpdateRecopiesTheCoordinatesWhenTheServiceLocationChanges() throws Exception {
        VendorFixture vendor = registerWithVendor();
        Long otherLocationId = serviceLocationRepository.findByPincode("560038").orElseThrow().getId();

        VendorProfileUpdateRequest update = new VendorProfileUpdateRequest();
        update.setBusinessName("Moved Blossoms");
        update.setAddressLine1("5 Elsewhere");
        update.setServiceLocationId(otherLocationId);

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pincode").value("560038"));

        VendorProfile reloaded =
                vendorProfileRepository.findByIdWithDetails(vendor.profileId()).orElseThrow();
        assertEquals(otherLocationId, reloaded.getServiceLocation().getId());
        assertEquals(reloaded.getServiceLocation().getLatitude(), reloaded.getLatitude());
        assertEquals(reloaded.getServiceLocation().getLongitude(), reloaded.getLongitude());
    }

    @Test
    void anUpdateRejectsAnInvalidWeek() throws Exception {
        VendorFixture vendor = registerWithVendor();

        VendorProfileUpdateRequest update = new VendorProfileUpdateRequest();
        update.setBusinessName("Test Blossoms");
        update.setAddressLine1("12 Test Street");
        update.setServiceLocationId(koramangalaId);
        update.setHours(List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.TUESDAY).closed(false)
                .openTime(LocalTime.of(18, 0)).closeTime(LocalTime.of(9, 0)).build()));

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendor.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        VendorProfile reloaded =
                vendorProfileRepository.findByIdWithDetails(vendor.profileId()).orElseThrow();
        assertEquals("Test Blossoms", reloaded.getBusinessName(),
                "a rejected update must not change the profile");
    }

    @Test
    void anAccountWithoutAProfileGetsNotFound() throws Exception {
        User florist = createUser(uniqueEmail(), "FLORIST");

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(florist))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updatingWithoutAProfileGetsNotFound() throws Exception {
        User florist = createUser(uniqueEmail(), "FLORIST");
        String token = tokenFor(florist);

        VendorProfileUpdateRequest update = new VendorProfileUpdateRequest();
        update.setBusinessName("Ghost Blossoms");
        update.setAddressLine1("1 Nowhere");
        update.setServiceLocationId(koramangalaId);

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertEquals(0L, countProfilesNamed("Ghost Blossoms"),
                "a refused update must not create a profile");
    }

    // ================================================================ authorization

    @Test
    void readingTheProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatingTheProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerCannotReachTheVendorProfile() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCustomerCannotUpdateTheVendorProfile() throws Exception {
        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aVendorCannotReachTheAdminVendorRoutes() throws Exception {
        VendorFixture vendor = registerWithVendor();
        String token = vendor.token();

        mockMvc.perform(get("/api/v1/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        for (String action : List.of("approve", "reinstate")) {
            mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/" + action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isForbidden());
        }
        for (String action : List.of("reject", "suspend")) {
            mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/" + action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason("not an admin")))
                    .andExpect(status().isForbidden());
        }
        assertEquals(VendorProfile.Status.PENDING_APPROVAL,
                vendorProfileRepository.findById(vendor.profileId()).orElseThrow().getStatus(),
                "a refused call must leave the profile untouched");
    }

    @Test
    void aCustomerCannotReachTheAdminVendorRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/vendors/1/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"fraud\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theAdminVendorRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/vendors/1/approve"))
                .andExpect(status().isUnauthorized());
    }

    // ======================================================= cross-vendor isolation

    @Test
    void oneVendorNeverSeesAnotherVendorsProfile() throws Exception {
        VendorFixture first = registerWithVendor();
        VendorFixture second = registerWithVendor();
        assertNotEquals(first.profileId(), second.profileId());

        mockMvc.perform(get("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.profileId().intValue()))
                .andExpect(jsonPath("$.ownerEmail").value(second.email()))
                .andExpect(jsonPath("$.ownerEmail").value(not(first.email())));
    }

    @Test
    void oneVendorCannotModifyAnotherVendorsProfile() throws Exception {
        VendorFixture victim = registerWithVendor();
        VendorFixture attacker = registerWithVendor();

        VendorProfileUpdateRequest attack = new VendorProfileUpdateRequest();
        attack.setBusinessName("Attacker Was Here");
        attack.setAddressLine1("666 Evil Lane");
        attack.setServiceLocationId(koramangalaId);

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attack)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerEmail").value(attacker.email()));

        VendorProfile victimReload =
                vendorProfileRepository.findByIdWithDetails(victim.profileId()).orElseThrow();
        assertEquals("Test Blossoms", victimReload.getBusinessName(),
                "the attacker's PUT must only touch the attacker's own profile");
        assertEquals("12 Test Street", victimReload.getAddressLine1());
    }

    @Test
    void aVendorIdentifierInThePathCannotRedirectTheUpdate() throws Exception {
        VendorFixture victim = registerWithVendor();
        VendorFixture attacker = registerWithVendor();

        // There is no id-bearing vendor route: "profile" is the only path segment.
        // A guessed path must not exist and must change nothing.
        mockMvc.perform(put("/api/v1/vendors/" + victim.profileId() + "/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(attacker.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        VendorProfile victimReload =
                vendorProfileRepository.findByIdWithDetails(victim.profileId()).orElseThrow();
        assertEquals("Test Blossoms", victimReload.getBusinessName());
    }

    // ========================================================= task 2.6: admin routes

    @Test
    void anAdminCanListVendorsAndFilterByStatus() throws Exception {
        VendorFixture first = registerWithVendor();
        VendorFixture second = registerWithVendor();

        List<Long> all = listedIds(null);
        assertTrue(all.contains(first.profileId()));
        assertTrue(all.contains(second.profileId()));

        List<Long> pending = listedIds("PENDING_APPROVAL");
        assertTrue(pending.contains(first.profileId()));
        assertTrue(pending.contains(second.profileId()));

        approve(first.profileId());

        assertTrue(listedIds("APPROVED").contains(first.profileId()));
        assertFalse(listedIds("APPROVED").contains(second.profileId()),
                "an unapproved profile must not appear under the APPROVED filter");
        assertFalse(listedIds("PENDING_APPROVAL").contains(first.profileId()),
                "approving a profile must remove it from the PENDING_APPROVAL filter");
    }

    /**
     * Reads every page of the admin vendor listing for one status filter. The
     * shared singleton MySQL container accumulates profiles across the whole
     * integration run, so a single page is never guaranteed to hold the rows this
     * test just created (see {@code docs/known-issues.md}).
     */
    private List<Long> listedIds(String status) throws Exception {
        List<Long> ids = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            MockHttpServletRequestBuilder request = get("/api/v1/admin/vendors")
                    .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                    .param("page", String.valueOf(page))
                    .param("size", "100");
            if (status != null) {
                request.param("status", status);
            }
            MvcResult result = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            for (JsonNode item : body.path("content")) {
                ids.add(item.path("id").asLong());
            }
            totalPages = body.path("totalPages").asInt(0);
            page++;
        } while (page < totalPages);
        return ids;
    }

    @Test
    void approvingWritesAnAuditRowAndFlipsTheStatus() throws Exception {
        VendorFixture vendor = registerWithVendor();

        mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        VendorProfile reloaded =
                vendorProfileRepository.findByIdWithDetails(vendor.profileId()).orElseThrow();
        assertEquals(VendorProfile.Status.APPROVED, reloaded.getStatus());

        var entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(
                VendorAdminService.ENTITY_TYPE, vendor.profileId());
        assertEquals(1, entries.size());
        assertEquals(VendorAdminService.ACTION_APPROVED, entries.get(0).getActionType());
        assertEquals("VENDOR_PROFILE", entries.get(0).getEntityType());
        assertNull(entries.get(0).getReason());
        assertEquals(adminUserId, entries.get(0).getActor().getId(),
                "the audit row must record the acting admin, not the vendor owner");
    }

    @Test
    void rejectingRequiresAReasonAndRecordsIt() throws Exception {
        VendorFixture vendor = registerWithVendor();

        mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/reject")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                VendorAdminReasonRequest.builder()
                                        .reason("Business licence could not be verified")
                                        .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        var entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(
                VendorAdminService.ENTITY_TYPE, vendor.profileId());
        assertEquals(1, entries.size());
        assertEquals(VendorAdminService.ACTION_REJECTED, entries.get(0).getActionType());
        assertEquals("Business licence could not be verified", entries.get(0).getReason());
        assertEquals(adminUserId, entries.get(0).getActor().getId());
    }

    @Test
    void theFullApprovalLifecycleIsAudited() throws Exception {
        VendorFixture vendor = registerWithVendor();
        Long id = vendor.profileId();

        approve(id);

        mockMvc.perform(post("/api/v1/admin/vendors/" + id + "/suspend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                VendorAdminReasonRequest.builder()
                                        .reason("Repeated late deliveries").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(post("/api/v1/admin/vendors/" + id + "/reinstate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        var entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdAsc(
                VendorAdminService.ENTITY_TYPE, id);
        assertEquals(3, entries.size());
        assertEquals(VendorAdminService.ACTION_APPROVED, entries.get(0).getActionType());
        assertEquals(VendorAdminService.ACTION_SUSPENDED, entries.get(1).getActionType());
        assertEquals("Repeated late deliveries", entries.get(1).getReason());
        assertEquals(VendorAdminService.ACTION_REINSTATED, entries.get(2).getActionType());
        assertTrue(entries.stream().allMatch(entry -> adminUserId.equals(entry.getActor().getId())),
                "every administrative transition must be attributed to the acting admin");
    }

    /**
     * The full legal/illegal transition matrix at the HTTP boundary. Each action
     * route accepts exactly one source status; every other combination must be a
     * 409 that names both the action and the status it refused, leaves the stored
     * status untouched, and writes no audit row.
     */
    @Test
    void everyIllegalTransitionIsRefusedWithAConflictThatChangesNothing() throws Exception {
        record Refused(VendorProfile.Status from, String action) {
        }

        List<Refused> illegal = List.of(
                new Refused(VendorProfile.Status.PENDING_APPROVAL, "suspend"),
                new Refused(VendorProfile.Status.PENDING_APPROVAL, "reinstate"),
                new Refused(VendorProfile.Status.APPROVED, "approve"),
                new Refused(VendorProfile.Status.APPROVED, "reject"),
                new Refused(VendorProfile.Status.APPROVED, "reinstate"),
                new Refused(VendorProfile.Status.REJECTED, "approve"),
                new Refused(VendorProfile.Status.REJECTED, "reject"),
                new Refused(VendorProfile.Status.REJECTED, "suspend"),
                new Refused(VendorProfile.Status.REJECTED, "reinstate"),
                new Refused(VendorProfile.Status.SUSPENDED, "approve"),
                new Refused(VendorProfile.Status.SUSPENDED, "reject"),
                new Refused(VendorProfile.Status.SUSPENDED, "suspend"));

        for (Refused refused : illegal) {
            VendorFixture vendor = registerInStatus(refused.from());
            long auditsBefore = auditLogRepository.countByEntityTypeAndEntityId(
                    VendorAdminService.ENTITY_TYPE, vendor.profileId());

            performAdminAction(vendor.profileId(), refused.action())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CONFLICT"))
                    .andExpect(jsonPath("$.message").value("Cannot " + refused.action()
                            + " a vendor profile in status " + refused.from()));

            assertEquals(refused.from(),
                    vendorProfileRepository.findById(vendor.profileId()).orElseThrow().getStatus(),
                    () -> refused.action() + " must not change a " + refused.from() + " profile");
            assertEquals(auditsBefore,
                    auditLogRepository.countByEntityTypeAndEntityId(
                            VendorAdminService.ENTITY_TYPE, vendor.profileId()),
                    () -> refused.action() + " must not audit a refused transition");
        }
    }

    /**
     * The four legal transitions, each from its one permitted source status.
     */
    @Test
    void everyLegalTransitionIsAcceptedFromExactlyOneStatus() throws Exception {
        record Allowed(VendorProfile.Status from, String action, VendorProfile.Status to) {
        }

        List<Allowed> legal = List.of(
                new Allowed(VendorProfile.Status.PENDING_APPROVAL, "approve", VendorProfile.Status.APPROVED),
                new Allowed(VendorProfile.Status.PENDING_APPROVAL, "reject", VendorProfile.Status.REJECTED),
                new Allowed(VendorProfile.Status.APPROVED, "suspend", VendorProfile.Status.SUSPENDED),
                new Allowed(VendorProfile.Status.SUSPENDED, "reinstate", VendorProfile.Status.APPROVED));

        for (Allowed allowed : legal) {
            VendorFixture vendor = registerInStatus(allowed.from());
            long auditsBefore = auditLogRepository.countByEntityTypeAndEntityId(
                    VendorAdminService.ENTITY_TYPE, vendor.profileId());

            performAdminAction(vendor.profileId(), allowed.action())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(allowed.to().name()));

            assertEquals(allowed.to(),
                    vendorProfileRepository.findById(vendor.profileId()).orElseThrow().getStatus());
            assertEquals(auditsBefore + 1,
                    auditLogRepository.countByEntityTypeAndEntityId(
                            VendorAdminService.ENTITY_TYPE, vendor.profileId()),
                    () -> allowed.action() + " must write exactly one audit row");
        }
    }

    @Test
    void anIllegalTransitionIsRejectedAndWritesNoAuditRow() throws Exception {
        VendorFixture vendor = registerWithVendor();
        approve(vendor.profileId());

        mockMvc.perform(post("/api/v1/admin/vendors/" + vendor.profileId() + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        "Cannot approve a vendor profile in status APPROVED"));

        assertEquals(1L, auditLogRepository.countByEntityTypeAndEntityId(
                        VendorAdminService.ENTITY_TYPE, vendor.profileId()),
                "a rejected transition must not leave an audit row");
    }

    @Test
    void anUnknownProfileIsNotFound() throws Exception {
        for (String action : List.of("approve", "reinstate")) {
            mockMvc.perform(post("/api/v1/admin/vendors/99999999/" + action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        for (String action : List.of("reject", "suspend")) {
            mockMvc.perform(post("/api/v1/admin/vendors/99999999/" + action)
                            .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(reason("missing profile")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
    }

    @Test
    void aNonPositiveProfileIdIsRejected() throws Exception {
        // @Positive on the path variable raises a ConstraintViolationException, which the
        // handler reports as VALIDATION_FAILED without a per-field validation map.
        mockMvc.perform(post("/api/v1/admin/vendors/0/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message",
                        org.hamcrest.Matchers.containsString("must be positive")));
    }

    @Test
    void anUnknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .param("status", "NOPE"))
                .andExpect(status().isBadRequest());
    }

    // ============================================================ migration integrity

    @Test
    void theAuditLogTableExistsAfterMigration() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = 'audit_log'",
                Long.class);
        assertEquals(1L, count);
    }

    @Test
    void theDatabaseRejectsANegativeReviewCount() {
        User owner = createUser(uniqueEmail(), "FLORIST");
        ServiceLocation location = location();

        assertCheckViolation("ck_vendor_profiles_review_count", () -> persistProfile(
                owner, location, -1, 30));
    }

    @Test
    void theDatabaseRejectsZeroPrepTime() {
        User owner = createUser(uniqueEmail(), "FLORIST");
        ServiceLocation location = location();

        assertCheckViolation("ck_vendor_profiles_prep_time", () -> persistProfile(
                owner, location, 0, 0));
    }

    @Test
    void theDatabaseRejectsANegativeFee() {
        User owner = createUser(uniqueEmail(), "FLORIST");
        ServiceLocation location = location();

        assertCheckViolation("ck_vendor_profiles_money_non_negative", () ->
                transactionTemplate.executeWithoutResult(status -> {
                    VendorProfile invalid = baseProfile(owner, location, 0, 30);
                    invalid.setPerKmFee(new BigDecimal("-1.00"));
                    vendorProfileRepository.saveAndFlush(invalid);
                }));
    }

    @Test
    void theDatabaseRejectsAClosedDayThatCarriesTimes() throws Exception {
        VendorFixture vendor = registerWithVendor();

        assertCheckViolation("ck_vendor_hours_times", () -> persistHours(
                vendor.profileId(), DayOfWeek.SATURDAY, true,
                LocalTime.of(9, 0), LocalTime.of(18, 0)));
    }

    @Test
    void theDatabaseRejectsAnInvertedTimeRange() throws Exception {
        VendorFixture vendor = registerWithVendor();

        assertCheckViolation("ck_vendor_hours_times", () -> persistHours(
                vendor.profileId(), DayOfWeek.SATURDAY, false,
                LocalTime.of(18, 0), LocalTime.of(9, 0)));
    }

    @Test
    void theDatabaseAcceptsAWellFormedWeek() throws Exception {
        VendorFixture vendor = registerWithVendor();

        persistHours(vendor.profileId(), DayOfWeek.SATURDAY, false,
                LocalTime.of(9, 0), LocalTime.of(18, 0));

        assertTrue(vendorHoursRepository
                .findByVendorProfileIdAndWeekday(vendor.profileId(), DayOfWeek.SATURDAY)
                .isPresent());
    }

    // ======================================================================= helpers

    private record VendorFixture(String email, String token, Long profileId) {
    }

    private void persistProfile(User owner, ServiceLocation location, int reviewCount, int prepTime) {
        transactionTemplate.executeWithoutResult(status -> {
            VendorProfile profile = baseProfile(owner, location, reviewCount, prepTime);
            vendorProfileRepository.saveAndFlush(profile);
        });
    }

    private static VendorProfile baseProfile(User owner, ServiceLocation location,
                                             int reviewCount, int prepTime) {
        return VendorProfile.builder()
                .user(owner)
                .businessName("Constraint Probe")
                .addressLine1("1 Probe Street")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .reviewCount(reviewCount)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(prepTime)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build();
    }

    private void persistHours(Long profileId, DayOfWeek weekday, boolean closed,
                              LocalTime open, LocalTime close) {
        transactionTemplate.executeWithoutResult(status -> {
            VendorProfile profile = vendorProfileRepository.findById(profileId).orElseThrow();
            vendorHoursRepository.saveAndFlush(VendorHours.builder()
                    .vendorProfile(profile)
                    .weekday(weekday)
                    .openTime(open)
                    .closeTime(close)
                    .closed(closed)
                    .build());
        });
    }

    private VendorFixture registerWithVendor() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest(email))))
                .andExpect(status().isCreated());

        User user = userRepository.findByEmail(email).orElseThrow();
        VendorProfile profile = vendorProfileRepository.findByUserId(user.getId()).orElseThrow();
        return new VendorFixture(email, tokenFor(user), profile.getId());
    }

    private void register(String email) throws Exception {
        register(registerRequest(email));
    }

    private void register(VendorRegisterRequest request) throws Exception {
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    private void approve(Long profileId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
    }

    /**
     * Invokes one administrative vendor action. {@code reject} and {@code suspend}
     * require a reason body; {@code approve} and {@code reinstate} take none, and
     * an unused body is ignored by their handlers, so one call serves all four.
     */
    private ResultActions performAdminAction(Long profileId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/vendors/" + profileId + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reason("transition matrix probe")));
    }

    /** Registers a vendor and drives it to the requested approval status by legal transitions only. */
    private VendorFixture registerInStatus(VendorProfile.Status target) throws Exception {
        VendorFixture vendor = registerWithVendor();
        switch (target) {
            case PENDING_APPROVAL -> {
                // freshly registered
            }
            case APPROVED -> approve(vendor.profileId());
            case REJECTED -> performAdminAction(vendor.profileId(), "reject")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("REJECTED"));
            case SUSPENDED -> {
                approve(vendor.profileId());
                performAdminAction(vendor.profileId(), "suspend")
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("SUSPENDED"));
            }
            default -> throw new IllegalArgumentException("unhandled status " + target);
        }
        assertEquals(target,
                vendorProfileRepository.findById(vendor.profileId()).orElseThrow().getStatus());
        return vendor;
    }

    private long countProfilesNamed(String businessName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vendor_profiles WHERE business_name = ?",
                Long.class, businessName);
    }

    private static String reason(String text) throws Exception {
        return new ObjectMapper().writeValueAsString(
                VendorAdminReasonRequest.builder().reason(text).build());
    }

    private ServiceLocation location() {
        return serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE).orElseThrow();
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

    private VendorRegisterRequest registerRequest(String email) {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        request.setFullName("Vendor Owner");
        request.setPhone(uniquePhone());
        request.setBusinessName("Test Blossoms");
        request.setDescription("Fresh flowers delivered daily");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(koramangalaId);
        request.setDeliveryRadiusKm(new BigDecimal("6.50"));
        request.setMinOrderAmount(new BigDecimal("199.99"));
        request.setBaseDeliveryFee(new BigDecimal("25.50"));
        request.setPerKmFee(new BigDecimal("1.75"));
        request.setPrepTimeMinutes(45);
        request.setSlotDurationMinutes(60);
        request.setMaxOrdersPerSlot(10);
        request.setAcceptingOrders(true);
        return request;
    }

    private static VendorHoursRequest openDay(DayOfWeek weekday, LocalTime open, LocalTime close) {
        return VendorHoursRequest.builder()
                .weekday(weekday).openTime(open).closeTime(close).closed(false).build();
    }

    /**
     * Asserts that the given write is refused by the named MySQL CHECK
     * constraint. MySQL reports a CHECK violation as SQL error 3819, which
     * Hibernate surfaces as a {@link org.springframework.orm.jpa.JpaSystemException}
     * rather than a {@link DataIntegrityViolationException}, so the constraint
     * name is asserted to prove the intended rule fired and not some other one.
     */
    private void assertCheckViolation(String constraintName, org.junit.jupiter.api.function.Executable write) {
        Exception thrown = assertThrows(Exception.class, write,
                () -> "expected the write to be rejected by " + constraintName);

        String message = thrownMessageChain(thrown);
        assertTrue(message.contains(constraintName),
                () -> "expected " + constraintName + " in the failure chain but got: " + message);
    }

    private static String thrownMessageChain(Throwable thrown) {
        StringBuilder chain = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            chain.append(current.getClass().getSimpleName()).append(": ")
                    .append(current.getMessage()).append('\n');
            if (current.getCause() == current) {
                break;
            }
        }
        return chain.toString();
    }

    private static String uniqueEmail() {
        return "vendor-" + UUID.randomUUID() + "@test.com";
    }

    /**
     * Ten random decimal digits. {@link UUID#randomUUID()} cannot be used for the
     * suffix because its hex output contains letters, which the request DTO's
     * {@code ^\+?[0-9]{7,15}$} phone pattern rejects.
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
