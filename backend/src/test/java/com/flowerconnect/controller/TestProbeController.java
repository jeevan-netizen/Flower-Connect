package com.flowerconnect.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TestProbeController {

    @GetMapping("/api/v1/users/me")
    String currentUser() {
        return "ok";
    }

    @GetMapping("/actuator/metrics")
    String metrics() {
        return "metrics";
    }
}
