package com.flowerconnect.security;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private static final String ADMIN_EMAIL_ENV = "ADMIN_EMAIL";
    private static final String ADMIN_PASSWORD_ENV = "ADMIN_PASSWORD";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String adminEmail = environment.getProperty(ADMIN_EMAIL_ENV);
        String adminPassword = environment.getProperty(ADMIN_PASSWORD_ENV);

        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            log.warn("Admin bootstrap skipped: {} and/or {} environment variables not set. "
                    + "Set these in your environment or .env file to create the initial ADMIN user.",
                    ADMIN_EMAIL_ENV, ADMIN_PASSWORD_ENV);
            return;
        }

        boolean adminExists = userRepository.existsByRoleName("ADMIN");
        if (adminExists) {
            log.info("Admin bootstrap skipped: ADMIN user already exists");
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found in database"));

        String normalizedEmail = adminEmail.trim().toLowerCase();
        User admin = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .fullName("Administrator")
                .phone(null)
                .role(adminRole)
                .status(User.Status.ACTIVE)
                .build();

        userRepository.save(admin);
        log.info("Created initial ADMIN user with email={}", normalizedEmail);
    }
}