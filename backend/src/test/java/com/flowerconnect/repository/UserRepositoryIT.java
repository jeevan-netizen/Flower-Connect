package com.flowerconnect.repository;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.test.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends IntegrationTestBase {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User createTestUser(String email) {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email(email)
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Test
    void shouldSaveAndFindByEmail() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user = User.builder()
                .email("test@test.com")
                .passwordHash("$2a$10$dummyhash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();

        User saved = userRepository.save(user);
        Optional<User> found = userRepository.findByEmail("test@test.com");

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void shouldCheckEmailExistence() {
        User user = createTestUser("exists@test.com");
        assertTrue(userRepository.existsByEmail("exists@test.com"));
        assertFalse(userRepository.existsByEmail("nope@test.com"));
    }

    @Test
    void shouldEnforceUniqueEmail() {
        Role role = roleRepository.findByName("CUSTOMER").orElseThrow();
        User user1 = User.builder()
                .email("unique@test.com")
                .passwordHash("$2a$10$hash1")
                .fullName("User One")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .email("unique@test.com")
                .passwordHash("$2a$10$hash2")
                .fullName("User Two")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .build();

        assertThrows(Exception.class, () -> {
            userRepository.saveAndFlush(user2);
        });
    }
}
