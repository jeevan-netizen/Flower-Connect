package com.flowerconnect.security;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.mapper.AdminUserMapper;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AdminUserPageResponse;
import com.flowerconnect.security.dto.AdminUserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the admin user listing of plan task 2.8.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AdminUserMapper adminUserMapper;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void listsEveryUserWhenNoFilterIsSupplied() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(2));
        stubMapping();

        AdminUserPageResponse response = adminUserService.listUsers(null, null, 0, 20);

        assertEquals(2, response.getContent().size());
        assertEquals(2L, response.getTotalElements());
        assertFalse(response.isEmpty());
        verify(userRepository).findAll(any(Pageable.class));
        verify(userRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void anEmptyPageIsReportedAsEmpty() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(0));

        AdminUserPageResponse response = adminUserService.listUsers(null, null, 0, 20);

        assertTrue(response.isEmpty());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    void aRoleFilterIsResolvedAgainstTheSeededRoles() {
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(role("CUSTOMER")));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(1));
        stubMapping();

        AdminUserPageResponse response =
                adminUserService.listUsers("customer", null, 0, 20);

        assertEquals(1, response.getContent().size());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void aStatusFilterDoesNotNeedARoleLookup() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(1));
        stubMapping();

        adminUserService.listUsers(null, User.Status.SUSPENDED, 0, 20);

        verifyNoInteractions(roleRepository);
    }

    @Test
    void roleAndStatusFiltersCombine() {
        when(roleRepository.findByName("FLORIST")).thenReturn(Optional.of(role("FLORIST")));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(pageOf(0));

        AdminUserPageResponse response =
                adminUserService.listUsers("FLORIST", User.Status.ACTIVE, 0, 20);

        assertTrue(response.isEmpty());
        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void anUnknownRoleIsRejected() {
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> adminUserService.listUsers("GHOST", null, 0, 20));

        assertEquals(ErrorCode.VALIDATION_FAILED, thrown.getErrorCode());
        assertEquals("Unknown role: GHOST", thrown.getMessage());
        verifyNoInteractions(userRepository, adminUserMapper);
    }

    @Test
    void pagingBoundsAreClamped() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.findAll(pageableCaptor.capture()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        adminUserService.listUsers(null, null, -3, 5_000);

        Pageable used = pageableCaptor.getValue();
        assertEquals(0, used.getPageNumber(), "a negative page must be clamped to the first page");
        assertEquals(100, used.getPageSize(), "an oversized page must be clamped to the maximum");
        assertNotNull(used.getSort().getOrderFor("createdAt"));
        assertNotNull(used.getSort().getOrderFor("id"));
    }

    @Test
    void anEmptyFilterValueIsTreatedAsNoFilter() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(pageOf(0));

        adminUserService.listUsers("   ", null, 0, 20);

        verifyNoInteractions(roleRepository);
    }

    // ------------------------------------------------------------------ helpers

    private void stubMapping() {
        when(adminUserMapper.toResponse(any()))
                .thenAnswer(invocation -> AdminUserResponse.builder()
                        .id(((User) invocation.getArgument(0)).getId())
                        .build());
    }

    private static PageImpl<User> pageOf(int size) {
        List<User> content = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            content.add(user((long) i + 1));
        }
        return new PageImpl<>(content, PageRequest.of(0, 20), size);
    }

    private static User user(Long id) {
        return User.builder()
                .id(id)
                .email("user-" + id + "@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .role(role("CUSTOMER"))
                .status(User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }

    private static Role role(String name) {
        return Role.builder().id(1L).name(name).build();
    }
}