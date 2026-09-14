package com.flowerconnect.test;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
public abstract class IntegrationTestBase {

    @Container
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("flowerconnect")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupTimeout(Duration.ofMinutes(3));

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }
}
