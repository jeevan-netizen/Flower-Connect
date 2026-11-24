package com.flowerconnect.geo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.geo.dto.ApproveServiceLocationProposalRequest;
import com.flowerconnect.geo.domain.ServiceLocationProposal;
import com.flowerconnect.geo.dto.ServiceLocationProposalRequest;
import com.flowerconnect.geo.dto.ServiceLocationProposalResponse;
import com.flowerconnect.geo.service.LocationProposalService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.domain.User;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * Web-layer coverage for the service-location proposal endpoints: submission is
 * open to any authenticated account, while listing and approving/rejecting are
 * administrator-only.
 */
@WebMvcTest(controllers = LocationProposalController.class)
@Import({LocationProposalControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class LocationProposalControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/locations/proposals")
                                .hasAnyRole("CUSTOMER", "FLORIST", "ADMIN")
                            .requestMatchers(HttpMethod.POST, "/api/v1/locations/proposals/**")
                                .hasRole("ADMIN")
                            .requestMatchers("/api/v1/locations/proposals")
                                .hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LocationProposalService proposalService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void submittingAProposalRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/locations/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validProposalRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "florist@example.com", roles = {"FLORIST"})
    void anAuthenticatedUserCanSubmitAProposal() throws Exception {
        ServiceLocationProposal proposal = ServiceLocationProposal.builder()
                .id(1L).city("Bengaluru").area("Indiranagar").status(ServiceLocationProposal.Status.PENDING).build();
        when(userRepository.findByEmailWithRole("florist@example.com"))
                .thenReturn(java.util.Optional.of(
                        com.flowerconnect.domain.User.builder().id(1L).email("florist@example.com").build()));
        when(proposalService.submitProposal(eq("Bengaluru"), eq("Indiranagar"), eq("560038"),
                eq("12 MG Road"), eq(null), eq(1L), eq("florist@example.com"))).thenReturn(proposal);

        mockMvc.perform(post("/api/v1/locations/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validProposalRequest())))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bengaluru")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listingProposalsIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/v1/locations/proposals"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/locations/proposals")
                        .header("Authorization", "Basic dummy"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void aCustomerCannotListProposals() throws Exception {
        mockMvc.perform(get("/api/v1/locations/proposals"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void anAdminCanListProposals() throws Exception {
        when(proposalService.listPending()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/locations/proposals"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void approvingAProposalCreatesTheServiceLocation() throws Exception {
        when(userRepository.findByEmailWithRole("admin@example.com"))
                .thenReturn(java.util.Optional.of(
                        com.flowerconnect.domain.User.builder().id(9L).email("admin@example.com").build()));
        when(proposalService.approveProposal(eq(1L), anyLong(), any()))
                .thenReturn(ServiceLocationProposalResponse.builder()
                        .id(1L).status("APPROVED").build());

        ApproveServiceLocationProposalRequest request = new ApproveServiceLocationProposalRequest();
        request.setLatitude(new java.math.BigDecimal("12.9352"));
        request.setLongitude(new java.math.BigDecimal("77.6245"));
        request.setTimezone("Asia/Kolkata");

        mockMvc.perform(post("/api/v1/locations/proposals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(proposalService).approveProposal(eq(1L), eq(9L), any());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void rejectingAProposalRecordsTheReason() throws Exception {
        when(userRepository.findByEmailWithRole("admin@example.com"))
                .thenReturn(java.util.Optional.of(
                        com.flowerconnect.domain.User.builder().id(9L).email("admin@example.com").build()));
        when(proposalService.rejectProposal(eq(1L), anyLong(), anyString()))
                .thenReturn(ServiceLocationProposalResponse.builder()
                        .id(1L).status("REJECTED").rejectionReason("duplicate area").build());

        VendorAdminReasonRequest request = new VendorAdminReasonRequest();
        request.setReason("duplicate area");

        mockMvc.perform(post("/api/v1/locations/proposals/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(proposalService).rejectProposal(1L, 9L, "duplicate area");
    }

    @Test
    @WithMockUser(username = "customer@example.com", roles = {"CUSTOMER"})
    void aCustomerCannotApproveOrReject() throws Exception {
        ApproveServiceLocationProposalRequest request = new ApproveServiceLocationProposalRequest();
        request.setLatitude(new java.math.BigDecimal("12.9352"));
        request.setLongitude(new java.math.BigDecimal("77.6245"));
        request.setTimezone("Asia/Kolkata");

        mockMvc.perform(post("/api/v1/locations/proposals/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        VendorAdminReasonRequest reject = new VendorAdminReasonRequest();
        reject.setReason("duplicate area");

        mockMvc.perform(post("/api/v1/locations/proposals/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reject)))
                .andExpect(status().isForbidden());

        verify(proposalService, never()).approveProposal(any(), any(), any());
        verify(proposalService, never()).rejectProposal(any(), any(), any());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static ServiceLocationProposalRequest validProposalRequest() {
        ServiceLocationProposalRequest request = new ServiceLocationProposalRequest();
        request.setCity("Bengaluru");
        request.setArea("Indiranagar");
        request.setPincode("560038");
        request.setAddressLine1("12 MG Road");
        return request;
    }
}
