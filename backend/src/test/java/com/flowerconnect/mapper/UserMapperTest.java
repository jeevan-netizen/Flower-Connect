package com.flowerconnect.mapper;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.security.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void shouldMapUserToUserResponse() {
        Role role = Role.builder().id(2L).name("FLORIST").build();
        User user = User.builder()
                .id(10L)
                .email("florist@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Florist Name")
                .phone("+1234567890")
                .role(role)
                .active(true)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse response = mapper.toResponse(user);

        assertEquals(10L, response.getId());
        assertEquals("florist@test.com", response.getEmail());
        assertEquals("Florist Name", response.getFullName());
        assertEquals("+1234567890", response.getPhone());
        assertEquals("FLORIST", response.getRole());
        assertEquals(LocalDateTime.of(2024, 1, 15, 10, 30), response.getCreatedAt());
    }

    @Test
    void shouldMapNullRoleToNull() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .role(null)
                .active(true)
                .build();

        UserResponse response = mapper.toResponse(user);

        assertNull(response.getRole());
        assertEquals("user@test.com", response.getEmail());
    }

    @Test
    void shouldMapNullPhone() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone(null)
                .role(Role.builder().id(1L).name("CUSTOMER").build())
                .active(true)
                .build();

        UserResponse response = mapper.toResponse(user);

        assertNull(response.getPhone());
        assertEquals("CUSTOMER", response.getRole());
    }
}
