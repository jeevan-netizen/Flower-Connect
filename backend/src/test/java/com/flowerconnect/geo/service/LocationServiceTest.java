package com.flowerconnect.geo.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import com.flowerconnect.geo.mapper.ServiceLocationMapper;
import com.flowerconnect.repository.ServiceLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private ServiceLocationRepository repository;

    @Mock
    private ServiceLocationMapper mapper;

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService(repository, mapper);
    }

    @Test
    void shouldFindAllHierarchical() {
        List<ServiceLocation> all = createSeedData();
        when(repository.findAll()).thenReturn(all);

        List<ServiceLocation> result = repository.findAll();
        assertFalse(result.isEmpty(), "Seed data should exist");
        assertEquals(8, result.size());
    }

    @Test
    void shouldFindByPincode() {
        ServiceLocation location = createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000");
        when(repository.findByPincode("560034")).thenReturn(Optional.of(location));

        Optional<ServiceLocation> result = locationService.findByPincode("560034");
        assertTrue(result.isPresent());
        assertEquals("Koramangala", result.get().getArea());
    }

    @Test
    void shouldReturnEmptyForUnknownPincode() {
        when(repository.findByPincode("999999")).thenReturn(Optional.empty());

        Optional<ServiceLocation> result = locationService.findByPincode("999999");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindByArea() {
        ServiceLocation location = createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000");
        when(repository.findByArea("Koramangala")).thenReturn(List.of(location));

        List<ServiceLocation> result = locationService.findByArea("Koramangala");
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("560034", result.get(0).getPincode());
    }

    @Test
    void shouldReturnEmptyForUnknownArea() {
        when(repository.findByArea("UnknownArea")).thenReturn(List.of());

        List<ServiceLocation> result = locationService.findByArea("UnknownArea");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSearchByPincode() {
        ServiceLocation location = createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000");
        Page<ServiceLocation> page = new PageImpl<>(List.of(location));
        when(repository.searchByPincodeAndArea(eq("560034"), eq(null), any(Pageable.class))).thenReturn(page);
        when(mapper.toFlatResponse(any())).thenReturn(List.of(
                ServiceLocationResponse.builder().city("Bengaluru").areas(List.of(
                        ServiceLocationResponse.AreaResponse.builder().area("Koramangala").pincode("560034").build()
                )).build()
        ));

        PageResponse<ServiceLocationResponse> result = locationService.search("560034", null, 0, 20);
        assertEquals(1, result.getContent().size());
        assertEquals("560034", result.getContent().get(0).getAreas().get(0).getPincode());
    }

    @Test
    void shouldSearchByArea() {
        ServiceLocation location = createLocation(2L, "Bengaluru", "Indiranagar", "560038", "12.97840000", "77.64080000");
        Page<ServiceLocation> page = new PageImpl<>(List.of(location));
        when(repository.searchByPincodeAndArea(eq(null), eq("Indiranagar"), any(Pageable.class))).thenReturn(page);
        when(mapper.toFlatResponse(any())).thenReturn(List.of(
                ServiceLocationResponse.builder().city("Bengaluru").areas(List.of(
                        ServiceLocationResponse.AreaResponse.builder().area("Indiranagar").pincode("560038").build()
                )).build()
        ));

        PageResponse<ServiceLocationResponse> result = locationService.search(null, "Indiranagar", 0, 20);
        assertEquals(1, result.getContent().size());
        assertEquals("Indiranagar", result.getContent().get(0).getAreas().get(0).getArea());
    }

    @Test
    void shouldSearchByPincodeAndAreaCombined() {
        ServiceLocation location = createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000");
        Page<ServiceLocation> page = new PageImpl<>(List.of(location));
        when(repository.searchByPincodeAndArea(eq("560034"), eq("Koramangala"), any(Pageable.class))).thenReturn(page);
        when(mapper.toFlatResponse(any())).thenReturn(List.of(
                ServiceLocationResponse.builder().city("Bengaluru").areas(List.of(
                        ServiceLocationResponse.AreaResponse.builder().area("Koramangala").pincode("560034").build()
                )).build()
        ));

        PageResponse<ServiceLocationResponse> result = locationService.search("560034", "Koramangala", 0, 20);
        assertEquals(1, result.getContent().size());
        assertEquals("560034", result.getContent().get(0).getAreas().get(0).getPincode());
        assertEquals("Koramangala", result.getContent().get(0).getAreas().get(0).getArea());
    }

    @Test
    void shouldReturnEmptyForNonMatchingPincodeAndArea() {
        Page<ServiceLocation> page = new PageImpl<>(List.of());
        when(repository.searchByPincodeAndArea(eq("560034"), eq("Indiranagar"), any(Pageable.class))).thenReturn(page);
        when(mapper.toFlatResponse(any())).thenReturn(List.of());

        PageResponse<ServiceLocationResponse> result = locationService.search("560034", "Indiranagar", 0, 20);
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldReturnEmptyForNoMatches() {
        Page<ServiceLocation> page = new PageImpl<>(List.of());
        when(repository.searchByPincodeAndArea(eq("999999"), eq(null), any(Pageable.class))).thenReturn(page);
        when(mapper.toFlatResponse(any())).thenReturn(List.of());

        PageResponse<ServiceLocationResponse> result = locationService.search("999999", null, 0, 20);
        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void shouldPaginateResults() {
        List<ServiceLocation> all = createSeedData();
        Page<ServiceLocation> page1 = new PageImpl<>(all.subList(0, 3), PageRequest.of(0, 3), 8);
        Page<ServiceLocation> page2 = new PageImpl<>(all.subList(3, 6), PageRequest.of(1, 3), 8);

        when(repository.searchByPincodeAndArea(eq(null), eq(null), any(Pageable.class))).thenReturn(page1, page2);
        doAnswer(invocation -> {
            List<ServiceLocation> input = invocation.getArgument(0);
            return input.stream()
                    .map(loc -> ServiceLocationResponse.builder()
                            .city(loc.getCity())
                            .areas(List.of(ServiceLocationResponse.AreaResponse.builder()
                                    .area(loc.getArea())
                                    .pincode(loc.getPincode())
                                    .latitude(loc.getLatitude())
                                    .longitude(loc.getLongitude())
                                    .build()))
                            .build())
                    .toList();
        }).when(mapper).toFlatResponse(any());

        PageResponse<ServiceLocationResponse> result1 = locationService.search(null, null, 0, 3);
        PageResponse<ServiceLocationResponse> result2 = locationService.search(null, null, 1, 3);

        assertEquals(3, result1.getContent().size());
        assertEquals(3, result2.getContent().size());
        assertEquals(8, result1.getTotalElements());
        assertEquals(3, result1.getTotalPages());
        assertNotEquals(result1.getContent().get(0).getAreas().get(0).getPincode(), result2.getContent().get(0).getAreas().get(0).getPincode());
    }

    @Test
    void shouldRespectMaxPageSize() {
        List<ServiceLocation> all = createSeedData();
        Page<ServiceLocation> page = new PageImpl<>(all, PageRequest.of(0, 100), 8);
        when(repository.searchByPincodeAndArea(eq(null), eq(null), any(Pageable.class))).thenReturn(page);
        doAnswer(invocation -> {
            List<ServiceLocation> input = invocation.getArgument(0);
            return input.stream()
                    .map(loc -> ServiceLocationResponse.builder()
                            .city(loc.getCity())
                            .areas(List.of(ServiceLocationResponse.AreaResponse.builder()
                                    .area(loc.getArea())
                                    .pincode(loc.getPincode())
                                    .latitude(loc.getLatitude())
                                    .longitude(loc.getLongitude())
                                    .build()))
                            .build())
                    .toList();
        }).when(mapper).toFlatResponse(any());

        PageResponse<ServiceLocationResponse> result = locationService.search(null, null, 0, 150);
        assertEquals(8, result.getContent().size());
        assertEquals(100, result.getSize());
    }

    @Test
    void shouldFindByCity() {
        List<ServiceLocation> all = createSeedData();
        when(repository.findByCity("Bengaluru")).thenReturn(all);

        List<ServiceLocation> result = locationService.findByCity("Bengaluru");
        assertEquals(8, result.size());
    }

    @Test
    void shouldFindByCityAndArea() {
        ServiceLocation location = createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000");
        when(repository.findByCityAndArea("Bengaluru", "Koramangala")).thenReturn(Optional.of(location));

        Optional<ServiceLocation> result = locationService.findByCityAndArea("Bengaluru", "Koramangala");
        assertTrue(result.isPresent());
        assertEquals("560034", result.get().getPincode());
    }

    @Test
    void shouldFindDistinctCities() {
        when(repository.findDistinctCities()).thenReturn(List.of("Bengaluru"));

        List<String> cities = locationService.findDistinctCities();
        assertEquals(1, cities.size());
        assertEquals("Bengaluru", cities.get(0));
    }

    private List<ServiceLocation> createSeedData() {
        return List.of(
                createLocation(1L, "Bengaluru", "Koramangala", "560034", "12.93520000", "77.62450000"),
                createLocation(2L, "Bengaluru", "Indiranagar", "560038", "12.97840000", "77.64080000"),
                createLocation(3L, "Bengaluru", "Whitefield", "560066", "12.96980000", "77.75000000"),
                createLocation(4L, "Bengaluru", "HSR Layout", "560102", "12.91160000", "77.64710000"),
                createLocation(5L, "Bengaluru", "Jayanagar", "560041", "12.92320000", "77.58360000"),
                createLocation(6L, "Bengaluru", "Malleshwaram", "560003", "13.00560000", "77.57070000"),
                createLocation(7L, "Bengaluru", "Electronic City", "560100", "12.84560000", "77.66030000"),
                createLocation(8L, "Bengaluru", "Marathahalli", "560037", "12.95920000", "77.69740000")
        );
    }

    private ServiceLocation createLocation(long id, String city, String area, String pincode, String lat, String lng) {
        return ServiceLocation.builder()
                .id(id)
                .city(city)
                .area(area)
                .pincode(pincode)
                .latitude(new BigDecimal(lat))
                .longitude(new BigDecimal(lng))
                .createdAt(LocalDateTime.now())
                .build();
    }
}