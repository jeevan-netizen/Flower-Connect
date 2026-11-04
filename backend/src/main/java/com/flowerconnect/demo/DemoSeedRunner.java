/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup ApplicationRunner for seeding dev demo data (Phase 14 task 14.5).
 *
 * <p>Hard rules enforced:
 * <ul>
 *   <li>Runs only under the {@code dev} profile (never in prod or test).</li>
 *   <li>Runs behind the {@code app.seed.enabled} property.</li>
 *   <li>Runs after {@link com.flowerconnect.security.AdminBootstrap} (order 100).</li>
 *   <li>Idempotent: safe to re-run on subsequent startups.</li>
 *   <li>Passwords configured via dev environment variables (DEMO_PASSWORD / DEMO_USER_PASSWORD);
 *       never committed to version control and never printed in logs or console output.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("dev")
@Order(100)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DemoSeedRunner implements ApplicationRunner {

    private final DemoSeedService demoSeedService;
    private final DemoSeedProperties properties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking dev demo seed data configuration...");

        // Resolve demo password from properties or environment
        String demoPassword = properties.getDemoPassword();
        if (demoPassword == null || demoPassword.isBlank()) {
            demoPassword = environment.getProperty("DEMO_PASSWORD");
        }
        if (demoPassword == null || demoPassword.isBlank()) {
            demoPassword = environment.getProperty("DEMO_USER_PASSWORD");
        }

        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("Demo seed data runner skipped: DEMO_PASSWORD (or app.seed.demo-password) is not set. "
                    + "Set DEMO_PASSWORD in your environment to automatically seed demo data.");
            return;
        }

        // Resolve admin email from properties or environment
        String adminEmail = properties.getAdminEmail();
        if (adminEmail == null || adminEmail.isBlank()) {
            adminEmail = environment.getProperty("ADMIN_EMAIL");
        }

        try {
            demoSeedService.seedAll(demoPassword, adminEmail);
            log.info("Demo seed data runner completed successfully.");
        } catch (Exception ex) {
            log.error("Failed to seed demo data: {}", ex.getMessage(), ex);
        }
    }
}

