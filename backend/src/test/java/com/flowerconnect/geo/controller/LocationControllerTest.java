package com.flowerconnect.geo.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.geo.dto.ServiceLocationResponse;
import com.flowerconnect.geo.service.LocationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class TestSecurityConfigForLocations {
    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/locations/**").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

@WebMvcTest(controllers = LocationController.class)
@Import({TestSecurityConfigForLocations.class, TestClockConfig.class})
@ActiveProfiles("test")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocationService locationService;

    @Test
    void shouldReturnAllLocationsHierarchical() throws Exception {
        ServiceLocationResponse.AreaResponse area = ServiceLocationResponse.AreaResponse.builder()
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .build();

        ServiceLocationResponse response = ServiceLocationResponse.builder()
                .city("Bengaluru")
                .areas(List.of(area))
                .build();

        when(locationService.findAllHierarchical()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("Bengaluru"))
                .andExpect(jsonPath("$[0].areas[0].area").value("Koramangala"))
                .andExpect(jsonPath("$[0].areas[0].pincode").value("560034"));
    }

    @Test
    void shouldSearchByPincode() throws Exception {
        ServiceLocation location = createServiceLocation("Bengaluru", "Koramangala", "560034",
                new BigDecimal("12.93520000"), new BigDecimal("77.62450000"));

        ServiceLocationResponse response = ServiceLocationResponse.builder()
                .city("Bengaluru")
                .areas(List.of(ServiceLocationResponse.AreaResponse.builder()
                        .area("Koramangala")
                        .pincode("560034")
                        .latitude(new BigDecimal("12.93520000"))
                        .longitude(new BigDecimal("77.62450000"))
                        .build()))
                .build();

        PageResponse<ServiceLocationResponse> pageResponse = PageResponse.<ServiceLocationResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(locationService.search(eq("560034"), eq(null), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "560034"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areas[0].pincode").value("560034"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSearchByArea() throws Exception {
        ServiceLocation location = createServiceLocation("Bengaluru", "Indiranagar", "560038",
                new BigDecimal("12.97840000"), new BigDecimal("77.64080000"));

        ServiceLocationResponse response = ServiceLocationResponse.builder()
                .city("Bengaluru")
                .areas(List.of(ServiceLocationResponse.AreaResponse.builder()
                        .area("Indiranagar")
                        .pincode("560038")
                        .latitude(new BigDecimal("12.97840000"))
                        .longitude(new BigDecimal("77.64080000"))
                        .build()))
                .build();

        PageResponse<ServiceLocationResponse> pageResponse = PageResponse.<ServiceLocationResponse>builder()
                .content(List.of(response))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(locationService.search(eq(null), eq("Indiranagar"), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/locations")
                        .param("area", "Indiranagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].areas[0].area").value("Indiranagar"));
    }

    @Test
    void shouldSearchByPincodeAndAreaCombined() throws Exception {
        PageResponse<ServiceLocationResponse> pageResponse = PageResponse.<ServiceLocationResponse>builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();

        when(locationService.search(eq("560034"), eq("Indiranagar"), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "560034")
                        .param("area", "Indiranagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReturnEmptyForNoMatches() throws Exception {
        PageResponse<ServiceLocationResponse> pageResponse = PageResponse.<ServiceLocationResponse>builder()
                .content(List.of())
                .page(0)
                .size(20)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();

        when(locationService.search(eq("999999"), eq(null), eq(0), eq(20)))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldRejectInvalidPincodeFormat() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "abc123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidPageParameter() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidSizeParameterTooSmall() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidSizeParameterTooLarge() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        ServiceLocationResponse.AreaResponse area = ServiceLocationResponse.AreaResponse.builder()
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .build();

        ServiceLocationResponse response = ServiceLocationResponse.builder()
                .city("Bengaluru")
                .areas(List.of(area))
                .build();

        when(locationService.findAllHierarchical()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk());
    }

    private ServiceLocation createServiceLocation(String city, String area, String pincode, BigDecimal lat, BigDecimal lng) {
        return ServiceLocation.builder()
                .id(1L)
                .city(city)
                .area(area)
                .pincode(pincode)
                .latitude(lat)
                .longitude(lng)
                .createdAt(LocalDateTime.now())
                .build();
    }
}