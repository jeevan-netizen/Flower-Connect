package com.flowerconnect.geo;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LocationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAllLocationsHierarchical() throws Exception {
        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].city").value("Bengaluru"))
                .andExpect(jsonPath("$[0].areas").isArray())
                .andExpect(jsonPath("$[0].areas[0].area").exists())
                .andExpect(jsonPath("$[0].areas[0].pincode").exists())
                .andExpect(jsonPath("$[0].areas[0].latitude").exists())
                .andExpect(jsonPath("$[0].areas[0].longitude").exists());
    }

    @Test
    void shouldSearchByPincode() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "560034"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].areas[0].pincode").value("560034"))
                .andExpect(jsonPath("$.content[0].areas[0].area").value("Koramangala"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSearchByArea() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("area", "Indiranagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].areas[0].area").value("Indiranagar"))
                .andExpect(jsonPath("$.content[0].areas[0].pincode").value("560038"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldSearchByPincodeAndAreaCombined() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "560034")
                        .param("area", "Koramangala"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].areas[0].pincode").value("560034"))
                .andExpect(jsonPath("$.content[0].areas[0].area").value("Koramangala"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldReturnEmptyForNonMatchingPincodeAndArea() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "560034")
                        .param("area", "Indiranagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldReturnEmptyForNoMatches() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void shouldPaginateResults() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("page", "0")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void shouldRespectMaxPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/locations"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidPincodeFormat() throws Exception {
        mockMvc.perform(get("/api/v1/locations")
                        .param("pincode", "abc123"))
                .andExpect(status().isBadRequest());
    }
}