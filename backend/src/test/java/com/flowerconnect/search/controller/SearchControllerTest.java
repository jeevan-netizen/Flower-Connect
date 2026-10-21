package com.flowerconnect.search.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.search.service.SearchService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class TestSecurityConfigForSearch {
    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/search").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

@WebMvcTest(controllers = SearchController.class)
@Import({TestSecurityConfigForSearch.class, TestClockConfig.class})
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void shouldReturnSearchResults() throws Exception {
        PublicProductResponse product = PublicProductResponse.builder()
                .id(100L)
                .vendorId(1L)
                .vendorName("Near Florist")
                .categoryId(1L)
                .categoryName("Roses")
                .name("Red Roses")
                .slug("red-roses")
                .description("Fresh red roses")
                .basePrice(new BigDecimal("199.00"))
                .available(10)
                .images(List.of())
                .build();

        PageResponse<PublicProductResponse> page = PageResponse.<PublicProductResponse>builder()
                .content(List.of(product))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(searchService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "1")
                        .param("q", "roses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Red Roses"))
                .andExpect(jsonPath("$.content[0].vendorName").value("Near Florist"))
                .andExpect(jsonPath("$.content[0].available").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void shouldRejectMissingLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectUnknownSort() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(com.flowerconnect.exception.BusinessException.badRequest("Unknown sort value: bogus"));

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "1")
                        .param("sort", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        when(searchService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PageResponse.<PublicProductResponse>builder()
                        .content(List.of())
                        .page(0)
                        .size(20)
                        .totalElements(0)
                        .totalPages(0)
                        .first(true)
                        .last(true)
                        .empty(true)
                        .build());

        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidPageParameter() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "1")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidSizeParameterTooLarge() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("locationId", "1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
