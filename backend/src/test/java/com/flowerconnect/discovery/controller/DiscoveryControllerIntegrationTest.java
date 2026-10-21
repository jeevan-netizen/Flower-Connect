package com.flowerconnect.discovery.controller;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import java.util.List;

import java.util.UUID;

import java.util.Random;
import java.util.ArrayList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DiscoveryControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String KORAMANGALA_PINCODE = "560034";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long koramangalaId;
    private String adminToken; // Not needed for public endpoint, but we might need to create vendors

    @BeforeEach
    void setUp() throws Exception {
        koramangalaId = serviceLocationRepository.findByPincode(KORAMANGALA_PINCODE)
                .orElseThrow().getId();
    }

    // Helper to create a user with a given role
    private User createUser(String email, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleName));
        String uniqueEmail = email.replaceFirst("@", UUID.randomUUID() + "@");
        Random random = new Random();
        String uniquePhone = String.format("%010d", random.nextInt(1000000000));

        User user = User.builder()
                .email(uniqueEmail)
                .passwordHash("dummy") // will be overridden
                .fullName("Test User")
                .phone(uniquePhone)
                .status(User.Status.ACTIVE) // Set status to active to avoid null constraint violation
                .build();
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    // Helper to create a vendor profile for a user at a given service location
    private VendorProfile createVendorProfile(User user, ServiceLocation location, BigDecimal deliveryRadiusKm, boolean acceptingOrders) {
        VendorProfile profile = VendorProfile.builder()
                .user(user)
                .businessName("Test Vendor " + UUID.randomUUID())
                .description("Test description")
                .addressLine1("123 Test Street")
                .addressLine2("")
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(deliveryRadiusKm)
                .status(VendorProfile.Status.APPROVED) // We need approved vendors for discovery
                .commissionRate(null)
                .avgRating(null)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.valueOf(100))
                .baseDeliveryFee(BigDecimal.valueOf(10))
                .perKmFee(BigDecimal.valueOf(2))
                .freeDeliveryAbove(BigDecimal.valueOf(1000))
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(acceptingOrders)
                .build();
        return vendorProfileRepository.saveAndFlush(profile);
    }

    /**
     * Walks every page of the discovery results for the given locationId and returns
     * all vendor IDs found. This keeps assertions independent of how many rows other
     * integration tests have left in the shared Testcontainers database.
     */
    private java.util.List<Integer> discoverAllVendorIds(Long locationId, int size) throws Exception {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        // First request to get totalPages
        String firstPageBody = mockMvc.perform(get("/api/v1/discover")
                        .param("locationId", locationId.toString())
                        .param("page", "0")
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int totalPages = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(firstPageBody).get("totalPages").asInt();

        // Extract IDs from first page
        for (com.fasterxml.jackson.databind.JsonNode node :
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(firstPageBody).get("content")) {
            ids.add(node.get("id").asInt());
        }

        // Fetch remaining pages
        for (int page = 1; page < totalPages; page++) {
            String body = mockMvc.perform(get("/api/v1/discover")
                            .param("locationId", locationId.toString())
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            for (com.fasterxml.jackson.databind.JsonNode node :
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("content")) {
                ids.add(node.get("id").asInt());
            }
        }
        return ids;
    }

    /**
     * Walks every page of the discovery results and returns the full vendor objects
     * (id and distanceKm) for assertions on ordering and distance.
     */
    private java.util.List<com.fasterxml.jackson.databind.JsonNode> discoverAllVendors(Long locationId, int size) throws Exception {
        java.util.List<com.fasterxml.jackson.databind.JsonNode> vendors = new java.util.ArrayList<>();
        String firstPageBody = mockMvc.perform(get("/api/v1/discover")
                        .param("locationId", locationId.toString())
                        .param("page", "0")
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int totalPages = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(firstPageBody).get("totalPages").asInt();

        for (com.fasterxml.jackson.databind.JsonNode node :
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(firstPageBody).get("content")) {
            vendors.add(node);
        }

        for (int page = 1; page < totalPages; page++) {
            String body = mockMvc.perform(get("/api/v1/discover")
                            .param("locationId", locationId.toString())
                            .param("page", String.valueOf(page))
                            .param("size", String.valueOf(size))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            for (com.fasterxml.jackson.databind.JsonNode node :
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("content")) {
                vendors.add(node);
            }
        }
        return vendors;
    }

    @Test
    void discoverReturnsVendorsWithinRadius() throws Exception {
        // Given: a service location (Koramangala) and two vendors:
        //   - Vendor A: very close (0 km), radius 5 km -> within
        //   - Vendor B: far away (100 km), radius 5 km -> out of range

        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId)
                .orElseThrow();

        // Vendor A: same location as origin (distance 0)
        User userA = createUser("vendorA@example.com", "FLORIST");
        VendorProfile vendorA = createVendorProfile(userA, origin, new BigDecimal("5"), true);

        // Vendor B: create at Koramangala then shift coordinates far away (1 degree ~ 111 km)
        User userB = createUser("vendorB@example.com", "FLORIST");
        VendorProfile vendorB = createVendorProfile(userB, origin, new BigDecimal("5"), true);
        BigDecimal oneDegree = new BigDecimal("1");
        vendorB.setLatitude(origin.getLatitude().add(oneDegree));
        vendorB.setLongitude(origin.getLongitude());
        vendorProfileRepository.saveAndFlush(vendorB);

        // When: we call the discovery endpoint for the origin location with large page size
        // Use size=100 to ensure our vendor appears in results despite shared DB having many vendors
        // Walk all pages to find our vendors (shared DB may have many vendors from other tests)
        java.util.List<com.fasterxml.jackson.databind.JsonNode> allVendors = discoverAllVendors(koramangalaId, 100);

        // Then: vendor A should be present with distance 0, vendor B should be excluded (out of radius)
        boolean foundA = false;
        for (com.fasterxml.jackson.databind.JsonNode v : allVendors) {
            if (v.get("id").asInt() == vendorA.getId().intValue()) {
                foundA = true;
                // Verify distance is 0
                double distance = v.get("distanceKm").asDouble();
                org.junit.jupiter.api.Assertions.assertEquals(0.0, distance, 0.001,
                        "Vendor A should have distance 0.0, got " + distance);
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(foundA, "Vendor A should be in discovery results");

        boolean foundB = false;
        for (com.fasterxml.jackson.databind.JsonNode v : allVendors) {
            if (v.get("id").asInt() == vendorB.getId().intValue()) {
                foundB = true;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(foundB, "Vendor B should NOT be in discovery results (out of radius)");

        // Clean up
        vendorProfileRepository.delete(vendorA);
        vendorProfileRepository.delete(vendorB);
        userRepository.delete(userA);
        userRepository.delete(userB);
    }

    @Test
    void discoverReturnsEmptyWhenNoApprovedVendors() throws Exception {
        // Given: the origin location, but we create a PENDING_APPROVAL vendor
        // (which should be excluded). Other approved vendors from other tests may exist,
        // so we only verify our vendor is not in results.

        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId)
                .orElseThrow();

        User user = createUser("vendorPending@example.com", "FLORIST");
        VendorProfile vendor = createVendorProfile(user, origin, new BigDecimal("5"), true);
        // Override status to PENDING_APPROVAL
        vendor.setStatus(VendorProfile.Status.PENDING_APPROVAL);
        vendorProfileRepository.saveAndFlush(vendor);

        // When: call discovery with large page size
        ResultActions result = mockMvc.perform(get("/api/v1/discover")
                .param("locationId", koramangalaId.toString())
                .param("page", "0")
                .param("size", "50")
                .accept(MediaType.APPLICATION_JSON));

        // Then: the PENDING_APPROVAL vendor should NOT be in results
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[*].id", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(vendor.getId().intValue()))));

        // Clean up
        vendorProfileRepository.delete(vendor);
        userRepository.delete(user);
    }

    @Test
    void discoverReturns400WhenLocationIdIsNull() throws Exception {
        // When: locationId is missing
        mockMvc.perform(get("/api/v1/discover")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Required parameter 'locationId' is not present"));
    }

    @Test
    void discoverReturns400WhenLocationIdIsUnknown() throws Exception {
        // Given: a locationId that does not exist
        Long unknownLocationId = 999999L;

        // When: call discovery with unknown locationId
        mockMvc.perform(get("/api/v1/discover")
                .param("locationId", unknownLocationId.toString())
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void discoverPaginationWorks() throws Exception {
        // Given: create three approved vendors at the same location (distance 0) with different IDs
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId)
                .orElseThrow();

        VendorProfile[] vendors = new VendorProfile[3];
        User[] users = new User[3];
        for (int i = 0; i < 3; i++) {
            users[i] = createUser("vendorPage" + i + "@example.com", "FLORIST");
            vendors[i] = createVendorProfile(users[i], origin, new BigDecimal("10"), true); // radius 10 km
            vendorProfileRepository.saveAndFlush(vendors[i]);
        }

        // Collect vendor IDs for assertions
        int[] vendorIds = new int[3];
        for (int i = 0; i < 3; i++) {
            vendorIds[i] = vendors[i].getId().intValue();
        }

        // When: request first page with size 2
        ResultActions result = mockMvc.perform(get("/api/v1/discover")
                .param("locationId", koramangalaId.toString())
                .param("page", "0")
                .param("size", "2")
                .accept(MediaType.APPLICATION_JSON));

        // Then: pagination structure is correct (size=2 returns 2 items per page)
        // Note: In shared DB, our vendors may not be on page 0 due to other test data,
        // so we only verify pagination metadata, not specific vendor IDs on specific pages.
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // And: when we request the second page, pagination still works
        result = mockMvc.perform(get("/api/v1/discover")
                .param("locationId", koramangalaId.toString())
                .param("page", "1")
                .param("size", "2")
                .accept(MediaType.APPLICATION_JSON));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.totalPages").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Clean up
        for (int i = 0; i < 3; i++) {
            vendorProfileRepository.delete(vendors[i]);
            userRepository.delete(users[i]);
        }
    }

@Test
    void discoverSortsByDistanceThenId() throws Exception {
        // Given: create three vendors at different distances from the origin
        ServiceLocation origin = serviceLocationRepository.findById(koramangalaId)
                .orElseThrow();

        // We'll create vendors at known distances by setting their latitude/longitude.
        // We want to test ordering, so we'll create:
        //   vendorA: distance 0 (same location)
        //   vendorB: distance 1 km
        //   vendorC: distance 2 km
        // But note: the Haversine distance is not linear in degrees, so we'll use approximations.
        // For simplicity, we'll adjust latitude only (since longitude adjustment depends on latitude).
        // 1 degree latitude ~ 111 km, so to get 1 km we need 1/111 degree.

        BigDecimal oneKmInLat = new BigDecimal("1").divide(new BigDecimal("111"), 6, BigDecimal.ROUND_HALF_UP);

        User userA = createUser("vendorSortA@example.com", "FLORIST");
        VendorProfile vendorA = createVendorProfile(userA, origin, new BigDecimal("10"), true);
        // vendorA at origin: distance 0

        User userB = createUser("vendorSortB@example.com", "FLORIST");
        VendorProfile vendorB = createVendorProfile(userB, origin, new BigDecimal("10"), true);
        vendorB.setLatitude(origin.getLatitude().add(oneKmInLat)); // approx 1 km north
        vendorB.setLongitude(origin.getLongitude());
        vendorProfileRepository.saveAndFlush(vendorB);

        User userC = createUser("vendorSortC@example.com", "FLORIST");
        VendorProfile vendorC = createVendorProfile(userC, origin, new BigDecimal("10"), true);
        vendorC.setLatitude(origin.getLatitude().add(oneKmInLat.multiply(new BigDecimal("2")))); // approx 2 km north
        vendorC.setLongitude(origin.getLongitude());
        vendorProfileRepository.saveAndFlush(vendorC);

        // When: call discovery with large page size, walk all pages
        java.util.List<com.fasterxml.jackson.databind.JsonNode> allVendors = discoverAllVendors(koramangalaId, 100);

        // Then: we should get our three vendors in the correct order (A, B, C by distance)
        // Other vendors from shared DB may appear, but our three should be in the right relative order
        // Extract our three vendors from the full result
        com.fasterxml.jackson.databind.JsonNode vA = null, vB = null, vC = null;
        for (com.fasterxml.jackson.databind.JsonNode v : allVendors) {
            int id = v.get("id").asInt();
            if (id == vendorA.getId().intValue()) vA = v;
            else if (id == vendorB.getId().intValue()) vB = v;
            else if (id == vendorC.getId().intValue()) vC = v;
        }

        org.junit.jupiter.api.Assertions.assertNotNull(vA, "Vendor A should be in results");
        org.junit.jupiter.api.Assertions.assertNotNull(vB, "Vendor B should be in results");
        org.junit.jupiter.api.Assertions.assertNotNull(vC, "Vendor C should be in results");

        // Verify distances
        double distA = vA.get("distanceKm").asDouble();
        double distB = vB.get("distanceKm").asDouble();
        double distC = vC.get("distanceKm").asDouble();

        org.junit.jupiter.api.Assertions.assertEquals(0.0, distA, 0.001,
                "Vendor A should have distance ~0.0, got " + distA);
        org.junit.jupiter.api.Assertions.assertTrue(distB < distC,
                "Vendor B (1km) should be closer than Vendor C (2km), got " + distB + " vs " + distC);
        org.junit.jupiter.api.Assertions.assertTrue(distA < distB,
                "Vendor A (0km) should be closer than Vendor B (1km), got " + distA + " vs " + distB);

        // Verify ordering in the full result list
        int indexA = -1, indexB = -1, indexC = -1;
        for (int i = 0; i < allVendors.size(); i++) {
            int id = allVendors.get(i).get("id").asInt();
            if (id == vendorA.getId().intValue()) indexA = i;
            else if (id == vendorB.getId().intValue()) indexB = i;
            else if (id == vendorC.getId().intValue()) indexC = i;
        }

        org.junit.jupiter.api.Assertions.assertTrue(indexA >= 0 && indexB >= 0 && indexC >= 0,
                "All three vendors should be in results");
        org.junit.jupiter.api.Assertions.assertTrue(indexA < indexB && indexB < indexC,
                "Vendors should be sorted by distance: A (0km) < B (1km) < C (2km). Indices: A=" + indexA + ", B=" + indexB + ", C=" + indexC);

// Clean up
        vendorProfileRepository.delete(vendorA);
        vendorProfileRepository.delete(vendorB);
        vendorProfileRepository.delete(vendorC);
        userRepository.delete(userA);
        userRepository.delete(userB);
        userRepository.delete(userC);
    }
}
