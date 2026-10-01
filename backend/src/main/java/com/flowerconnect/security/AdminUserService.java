package com.flowerconnect.security;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.mapper.AdminUserMapper;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.AdminUserPageResponse;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.specification.UserSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin user listing (plan task 2.8), backing the user list of the admin
 * dashboard (task 2.10) so an administrator can find the account whose status
 * they intend to change.
 *
 * <p>Pagination, filter semantics and the response shape follow the admin vendor
 * listing (task 2.6) so both admin screens behave identically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AdminUserMapper adminUserMapper;

    /**
     * Lists users, optionally narrowed to one role and/or one status. The two
     * filters combine with {@code AND}.
     *
     * @param roleName seeded role name, or {@code null} for no role filter
     * @throws BusinessException {@code VALIDATION_FAILED} when the named role is
     *                           not a seeded role
     */
    @Transactional(readOnly = true)
    public AdminUserPageResponse listUsers(String roleName, User.Status status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by("createdAt").and(Sort.by("id")));

        Specification<User> specification = buildSpecification(roleName, status);
        Page<User> result = specification == null
                ? userRepository.findAll(pageable)
                : userRepository.findAll(specification, pageable);

        List<AdminUserResponse> content = result.getContent().stream()
                .map(adminUserMapper::toResponse)
                .toList();

        return AdminUserPageResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    private Specification<User> buildSpecification(String roleName, User.Status status) {
        if (roleName != null && !roleName.isBlank()) {
            String normalized = roleName.trim().toUpperCase();
            if (roleRepository.findByName(normalized).isEmpty()) {
                throw BusinessException.badRequest("Unknown role: " + roleName);
            }
        }

        List<Specification<User>> filters = new ArrayList<>();
        if (roleName != null && !roleName.isBlank()) {
            filters.add(UserSpecifications.withRole(roleName.trim().toUpperCase()));
        }
        if (status != null) {
            filters.add(UserSpecifications.withStatus(status));
        }

        if (filters.isEmpty()) {
            return null;
        }
        Specification<User> combined = filters.get(0);
        for (int i = 1; i < filters.size(); i++) {
            combined = combined.and(filters.get(i));
        }
        return combined;
    }
}