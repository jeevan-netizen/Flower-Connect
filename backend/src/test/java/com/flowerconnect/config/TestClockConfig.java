package com.flowerconnect.config;

import com.flowerconnect.test.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock clock() {
        return new MutableClock(Instant.now());
    }
}
