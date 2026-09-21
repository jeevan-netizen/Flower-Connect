package com.flowerconnect.repository;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.test.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User createTestUser(String email) {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniquePhone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .phone(uniquePhone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Test
    void shouldSaveAndFindByEmail() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniqueEmail = "test-" + UUID.randomUUID() + "@test.com";
        String phone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user = User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .phone(phone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        Optional<User> found = userRepository.findByEmail(uniqueEmail);

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void shouldCheckEmailExistence() {
        String uniqueEmail = "exists-" + UUID.randomUUID() + "@test.com";
        User user = createTestUser(uniqueEmail);
        assertTrue(userRepository.existsByEmail(uniqueEmail));
        assertFalse(userRepository.existsByEmail("nope@test.com"));
    }

    @Test
    void shouldEnforceUniqueEmail() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        String uniqueEmail = "unique-" + UUID.randomUUID() + "@test.com";
        String phone = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user1 = User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$hash1")
                .fullName("User One")
                .phone(phone)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        userRepository.saveAndFlush(user1);

        String phone2 = "+1" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        User user2 = User.builder()
                .email(uniqueEmail)
                .passwordHash("$2a$10$hash2")
                .fullName("User Two")
                .phone(phone2)
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();

        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(user2);
        });
    }
}
