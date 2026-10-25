package com.flowerconnect.notification.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.notification.domain.Notification;
import com.flowerconnect.notification.mapper.NotificationMapper;
import com.flowerconnect.notification.service.NotificationService;
import com.flowerconnect.test.RoleBoundaryTester;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the notification read API (plan Phase 9, task 9.2).
 *
 * <p>The controller is thin: every read is delegated to
 * {@link NotificationService}. This slice mocks the service and asserts HTTP
 * wiring, status codes and the error envelope.
 *
 * <p>The production {@code SecurityConfig} is not loaded in a slice, so
 * {@link TestSecurityConfig} mirrors the production notifications namespace
 * rule (every authenticated role) for the {@code /api/v1/notifications/}
 * base.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import({NotificationControllerTest.TestSecurityConfig.class, TestClockConfig.class,
        NotificationControllerTest.TestMapperConfig.class})
class NotificationControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/notifications/**")
                            .hasAnyRole("CUSTOMER", "FLORIST", "ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @TestConfiguration
    static class TestMapperConfig {
        @Bean
        NotificationMapper notificationMapper() {
            return NotificationMapper.INSTANCE;
        }
    }

    private static final String BASE = "/api/v1/notifications";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void init() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
    }

    // ------------------------------------------------------------- role boundary

    @Test
    void everyEndpointRefusesAnonymousCallersAndAcceptsEveryAuthenticatedRole() throws Exception {
        when(notificationService.listNotifications(any(), any())).thenReturn(samplePage());
        when(notificationService.countUnread(any())).thenReturn(2L);
        when(notificationService.markAsRead(any(), anyLong())).thenReturn(sampleResponse());
        when(notificationService.markAllAsRead(any())).thenReturn(2);

        roleBoundaryTester.assertUnauthenticated(BASE, HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE, HttpMethod.GET, 200, 200, 200);

        roleBoundaryTester.assertUnauthenticated(BASE + "/unread-count", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/unread-count", HttpMethod.GET, 200, 200, 200);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/read", HttpMethod.PATCH, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1/read", HttpMethod.PATCH, 200, 200, 200);

        roleBoundaryTester.assertUnauthenticated(BASE + "/read-all", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/read-all", HttpMethod.POST, 200, 200, 200);
    }

    // ------------------------------------------------------------- list

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void listNotificationsReturnsTheCallersOwnPage() throws Exception {
        when(notificationService.listNotifications(eq("a@test.com"), any()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE).param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(notificationService).listNotifications(eq("a@test.com"), any());
    }

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void listNotificationsRejectsAnInvalidPageSize() throws Exception {
        mockMvc.perform(get(BASE).param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------- unread count

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void unreadCountReturnsTheCallersOwnCount() throws Exception {
        when(notificationService.countUnread(eq("a@test.com"))).thenReturn(3L);

        mockMvc.perform(get(BASE + "/unread-count"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").value(3));

        verify(notificationService).countUnread(eq("a@test.com"));
    }

    // ------------------------------------------------------------- mark one read

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void markAsReadReturnsTheUpdatedNotification() throws Exception {
        when(notificationService.markAsRead(eq("a@test.com"), eq(5L)))
                .thenReturn(sampleResponse());

        mockMvc.perform(patch(BASE + "/5/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.type").value("order_placed"))
                .andExpect(jsonPath("$.readAt").value("2026-10-25T10:00:00"));

        verify(notificationService).markAsRead(eq("a@test.com"), eq(5L));
    }

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void markAsReadRejectsANonPositiveId() throws Exception {
        mockMvc.perform(patch(BASE + "/0/read")).andExpect(status().isBadRequest());
        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------- mark all read

    @Test
    @WithMockUser(username = "a@test.com", roles = "CUSTOMER")
    void markAllAsReadReturnsTheNumberMarked() throws Exception {
        when(notificationService.markAllAsRead(eq("a@test.com"))).thenReturn(4);

        mockMvc.perform(post(BASE + "/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(4));

        verify(notificationService).markAllAsRead(eq("a@test.com"));
    }

    // ------------------------------------------------------------- fixtures

    private Notification sampleResponse() {
        return Notification.builder()
                .id(5L)
                .userId(7L)
                .type("order_placed")
                .title("Order placed")
                .message("Your order #5 is confirmed.")
                .targetType("ORDER")
                .targetId(5L)
                .readAt(LocalDateTime.of(2026, 10, 25, 10, 0, 0))
                .createdAt(LocalDateTime.of(2026, 10, 25, 9, 0, 0))
                .build();
    }

    private org.springframework.data.domain.Page<Notification> samplePage() {
        Notification n = Notification.builder()
                .id(5L)
                .userId(7L)
                .type("order_placed")
                .title("Order placed")
                .message("Your order #5 is confirmed.")
                .targetType("ORDER")
                .targetId(5L)
                .readAt(null)
                .createdAt(LocalDateTime.of(2026, 10, 25, 9, 0, 0))
                .build();
        return new PageImpl<>(List.of(n), PageRequest.of(0, 5), 1);
    }
}
