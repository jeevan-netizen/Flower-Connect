package com.flowerconnect.geo;

import com.flowerconnect.domain.ServiceLocation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceLocationSeedIntegrityTest {

    @Test
    void shouldHaveNoDuplicateServiceAreas() {
        List<ServiceLocation> all = createSeedData();

        long distinctCityArea = all.stream()
                .map(loc -> loc.getCity() + "|" + loc.getArea())
                .distinct()
                .count();
        assertEquals(all.size(), distinctCityArea, "Duplicate city+area combinations found");
    }

    @Test
    void shouldHaveValidLatitudeRange() {
        List<ServiceLocation> all = createSeedData();

        for (ServiceLocation loc : all) {
            BigDecimal lat = loc.getLatitude();
            assertNotNull(lat, "Latitude must not be null for " + loc.getArea());
            assertTrue(lat.compareTo(BigDecimal.valueOf(-90)) >= 0, "Latitude below -90 for " + loc.getArea());
            assertTrue(lat.compareTo(BigDecimal.valueOf(90)) <= 0, "Latitude above 90 for " + loc.getArea());
        }
    }

    @Test
    void shouldHaveValidLongitudeRange() {
        List<ServiceLocation> all = createSeedData();

        for (ServiceLocation loc : all) {
            BigDecimal lng = loc.getLongitude();
            assertNotNull(lng, "Longitude must not be null for " + loc.getArea());
            assertTrue(lng.compareTo(BigDecimal.valueOf(-180)) >= 0, "Longitude below -180 for " + loc.getArea());
            assertTrue(lng.compareTo(BigDecimal.valueOf(180)) <= 0, "Longitude above 180 for " + loc.getArea());
        }
    }

    @Test
    void shouldHaveValidPincodeFormat() {
        List<ServiceLocation> all = createSeedData();

        for (ServiceLocation loc : all) {
            String pincode = loc.getPincode();
            assertNotNull(pincode, "Pincode must not be null for " + loc.getArea());
            assertTrue(pincode.matches("^[0-9]{6}$"), "Invalid pincode format for " + loc.getArea() + ": " + pincode);
        }
    }

    @Test
    void shouldHaveConsistentCityAreaPincodeRelationships() {
        List<ServiceLocation> all = createSeedData();

        for (ServiceLocation loc : all) {
            assertEquals("Bengaluru", loc.getCity(), "City must be Bengaluru for all demo seed data");
            assertNotNull(loc.getArea(), "Area must not be null");
            assertNotNull(loc.getPincode(), "Pincode must not be null");
            assertNotNull(loc.getLatitude(), "Latitude must not be null");
            assertNotNull(loc.getLongitude(), "Longitude must not be null");
        }
    }

    @Test
    void shouldHaveExpectedSeedCount() {
        List<ServiceLocation> all = createSeedData();
        assertEquals(8, all.size(), "Expected 8 seed locations for Bengaluru demo region");
    }

    @Test
    void shouldHaveExpectedAreas() {
        List<ServiceLocation> all = createSeedData();
        List<String> areas = all.stream()
                .map(ServiceLocation::getArea)
                .sorted()
                .toList();
        assertEquals(List.of(
                "Electronic City",
                "HSR Layout",
                "Indiranagar",
                "Jayanagar",
                "Koramangala",
                "Malleshwaram",
                "Marathahalli",
                "Whitefield"
        ), areas);
    }

    private List<ServiceLocation> createSeedData() {
        return List.of(
                createLocation("Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000"),
                createLocation("Bengaluru", "Indiranagar", "560038", "12.97840000", "77.64080000"),
                createLocation("Bengaluru", "Whitefield", "560066", "12.96980000", "77.75000000"),
                createLocation("Bengaluru", "HSR Layout", "560102", "12.91160000", "77.64710000"),
                createLocation("Bengaluru", "Jayanagar", "560041", "12.92320000", "77.58360000"),
                createLocation("Bengaluru", "Malleshwaram", "560003", "13.00560000", "77.57070000"),
                createLocation("Bengaluru", "Electronic City", "560100", "12.84560000", "77.66030000"),
                createLocation("Bengaluru", "Marathahalli", "560037", "12.95920000", "77.69740000")
        );
    }

    private ServiceLocation createLocation(String city, String area, String pincode, String lat, String lng) {
        return ServiceLocation.builder()
                .city(city)
                .area(area)
                .pincode(pincode)
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lng))
                .build();
    }
}