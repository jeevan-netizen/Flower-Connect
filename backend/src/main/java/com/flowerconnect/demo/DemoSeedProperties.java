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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for dev demo seed data runner.
 * Disabled by default; active only in dev profile with demoPassword configured.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seed")
public class DemoSeedProperties {

    /**
     * Whether demo seed execution is enabled. Default false.
     */
    private boolean enabled = false;

    /**
     * Password assigned to seeded demo CUSTOMER and VENDOR users.
     * Must be supplied via environment variable (e.g. DEMO_PASSWORD) in dev.
     * Never commit plaintext passwords.
     */
    private String demoPassword;

    /**
     * Admin email to use for admin actions (approvals, dispute resolution, settlements).
     * Defaults to the ADMIN_EMAIL environment variable.
     */
    private String adminEmail;
}

