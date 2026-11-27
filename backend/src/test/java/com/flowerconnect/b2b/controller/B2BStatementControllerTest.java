package com.flowerconnect.b2b.controller;

import com.flowerconnect.b2b.domain.B2BStatement;
import com.flowerconnect.b2b.domain.B2BStatement.PaymentMode;
import com.flowerconnect.b2b.domain.B2BStatement.Status;
import com.flowerconnect.b2b.dto.B2BStatementPageResponse;
import com.flowerconnect.b2b.dto.B2BStatementPreviewResponse;
import com.flowerconnect.b2b.dto.B2BStatementResponse;
import com.flowerconnect.b2b.dto.CreateB2BStatementRequest;
import com.flowerconnect.b2b.dto.RecordB2BStatementPaymentRequest;
import com.flowerconnect.b2b.service.B2BStatementService;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.test.RoleBoundaryTester;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the B2B statement controller (Stage 3 of the B2B module).
 *
 * <p>The controller is thin: every action is delegated to
 * {@link B2BStatementService}. This slice mocks the service and asserts HTTP
 * wiring, status codes and the error envelope.
 *
 * <p>The production {@code SecurityConfig} is not loaded in a slice, so a
 * {@link TestSecurityConfig} mirrors the production FLORIST namespace rule
 * for the {@code /api/v1/vendors/b2b/} base. The approval gate
 * ({@code @RequiresApprovedVendor}) is deliberately not asserted here; it is
 * covered by {@code B2BStatementIntegrationTest} against the real filter chain.
 */
@WebMvcTest(controllers = B2BStatementController.class)
@Import({B2BStatementControllerTest.TestSecurityConfig.class, com.flowerconnect.config.TestClockConfig.class})
class B2BStatementControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendors/b2b/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String FLORIST = "florist@test.com";
    private static final String BASE = "/api/v1/vendors/b2b";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private B2BStatementService b2bStatementService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void init() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);

        when(b2bStatementService.preview(anyString(), anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePreview());
        when(b2bStatementService.createStatement(anyString(), any(CreateB2BStatementRequest.class)))
                .thenReturn(sampleResponse(Status.OPEN));
        when(b2bStatementService.listStatements(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(samplePage());
        when(b2bStatementService.getStatement(anyString(), anyLong()))
                .thenReturn(sampleResponse(Status.OPEN));
        when(b2bStatementService.recordPayment(anyString(), anyLong(), any(RecordB2BStatementPaymentRequest.class)))
                .thenReturn(sampleResponse(Status.PAID));
    }

    // ------------------------------------------------------------- role boundary

    @Test
    void everyEndpointRefusesNonVendorsAndAnonymousCallers() throws Exception {
        // Preview requires a buyer id; a florist without it gets 400.
        roleBoundaryTester.assertUnauthenticated(BASE + "/statements/preview", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/statements/preview", HttpMethod.GET, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/statements", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/statements", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/statements", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/statements", HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/statements/1", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/statements/1", HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/statements/1/payment", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/statements/1/payment", HttpMethod.POST, 403, 400, 403);
    }

    // ------------------------------------------------------------- actions

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void previewReturnsServiceResult() throws Exception {
        when(b2bStatementService.preview(eq(FLORIST), eq(2L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(samplePreview());

        mockMvc.perform(get(BASE + "/statements/preview")
                        .param("buyerId", "2"))
                .andExpect(status().isOk());

        verify(b2bStatementService).preview(FLORIST, 2L, null, null);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createStatementReturnsCreatedStatement() throws Exception {
        when(b2bStatementService.createStatement(eq(FLORIST), any(CreateB2BStatementRequest.class)))
                .thenReturn(sampleResponse(Status.OPEN));

        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(2L);

        mockMvc.perform(post(BASE + "/statements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(b2bStatementService).createStatement(eq(FLORIST), any(CreateB2BStatementRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listStatementsReturnsPage() throws Exception {
        when(b2bStatementService.listStatements(eq(FLORIST), anyString(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE + "/statements")
                        .param("role", "SELLER")
                        .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(b2bStatementService).listStatements(FLORIST, "SELLER", 0, 5);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void getStatementReturnsTheStatement() throws Exception {
        when(b2bStatementService.getStatement(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(Status.OPEN));

        mockMvc.perform(get(BASE + "/statements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(b2bStatementService).getStatement(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void recordPaymentReturnsPaidStatement() throws Exception {
        when(b2bStatementService.recordPayment(eq(FLORIST), eq(1L), any(RecordB2BStatementPaymentRequest.class)))
                .thenReturn(sampleResponse(Status.PAID));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.UPI);

        mockMvc.perform(post(BASE + "/statements/1/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(b2bStatementService).recordPayment(eq(FLORIST), eq(1L), any(RecordB2BStatementPaymentRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void recordPaymentRejectsAlreadyPaid() throws Exception {
        when(b2bStatementService.recordPayment(eq(FLORIST), eq(1L), any(RecordB2BStatementPaymentRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "Statement 1 is already PAID"));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(PaymentMode.CASH);

        mockMvc.perform(post(BASE + "/statements/1/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        verify(b2bStatementService).recordPayment(eq(FLORIST), eq(1L), any(RecordB2BStatementPaymentRequest.class));
    }

    // ------------------------------------------------------------- fixtures

    private B2BStatementResponse sampleResponse(Status status) {
        return B2BStatementResponse.builder()
                .id(1L)
                .statementNo("STM-1")
                .status(status)
                .totalAmount(BigDecimal.valueOf(597.00))
                .build();
    }

    private B2BStatementPageResponse samplePage() {
        return B2BStatementPageResponse.builder()
                .content(List.of(sampleResponse(Status.OPEN)))
                .page(0)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }

    private B2BStatementPreviewResponse samplePreview() {
        return B2BStatementPreviewResponse.builder()
                .totalAmount(BigDecimal.valueOf(597.00))
                .build();
    }
}
