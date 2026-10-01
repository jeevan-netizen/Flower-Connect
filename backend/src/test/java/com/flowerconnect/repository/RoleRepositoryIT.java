package com.flowerconnect.repository;

import com.flowerconnect.domain.Role;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void shouldFindRoleByName() {
        Optional<Role> role = roleRepository.findByName("CUSTOMER");
        assertTrue(role.isPresent());
        assertEquals("CUSTOMER", role.get().getName());
    }

    @Test
    void shouldFindAllRoles() {
        assertEquals(3, roleRepository.findAll().size());
    }

    @Test
    void shouldReturnEmptyWhenRoleNotFound() {
        Optional<Role> role = roleRepository.findByName("NONEXISTENT");
        assertTrue(role.isEmpty());
    }
}
