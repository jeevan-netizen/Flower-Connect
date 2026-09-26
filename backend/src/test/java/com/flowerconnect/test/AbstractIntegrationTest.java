package com.flowerconnect.test;

import com.flowerconnect.config.TestClockConfig;
import org.junit.jupiter.api.Tag;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("flowerconnect")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeout(Duration.ofMinutes(3));

    /**
     * Mailhog SMTP + HTTP API container. Testcontainers has no official
     * Mailhog module, so a {@link GenericContainer} is used instead. The HTTP
     * API at port 8025 is used by tests to assert that reset emails were sent
     * (see {@code PasswordResetIntegrationTest}).
     */
    protected static final GenericContainer<?> MAILHOG =
            new GenericContainer<>("mailhog/mailhog:v1.0.1")
                    .withExposedPorts(1025, 8025)
                    .withStartupTimeout(Duration.ofSeconds(30));

    static {
        MYSQL.start();
        MAILHOG.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Point the mail sender at the Testcontainers Mailhog instance.
        registry.add("spring.mail.host", MAILHOG::getHost);
        registry.add("spring.mail.port", () -> String.valueOf(MAILHOG.getMappedPort(1025)));
    }
}