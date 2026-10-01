package com.flowerconnect.config;

import com.flowerconnect.security.jwt.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

class AppPropertiesTest {

    @Test
    void shouldLoadDevDefaultBaseUrl() {
        AppProperties props = new AppProperties();
        assertEquals("http://localhost:5173", props.getBaseUrl());
    }

    @Test
    void shouldDefaultGraceWindowTo10Seconds() {
        JwtProperties props = new JwtProperties();
        assertEquals(10L, props.getRefreshGraceSeconds());
    }

    @Test
    void shouldFailFastWhenJwtSecretIsMissing() {
        JwtProperties props = new JwtProperties();
        JwtService service = new JwtService(props, Clock.systemUTC());
        assertThrows(IllegalStateException.class, service::init);
    }

    @Test
    void shouldFailFastWhenJwtSecretIsUnder256Bit() {
        JwtProperties props = new JwtProperties();
        props.setSecret("too-short");
        JwtService service = new JwtService(props, Clock.systemUTC());
        assertThrows(IllegalStateException.class, service::init);
    }
}
