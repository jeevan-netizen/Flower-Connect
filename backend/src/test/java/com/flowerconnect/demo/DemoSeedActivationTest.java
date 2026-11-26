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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoSeedActivationTest {

    @Configuration
    static class TestMockConfig {
        @Bean
        DemoSeedService demoSeedService() {
            return mock(DemoSeedService.class);
        }
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    TestMockConfig.class,
                    DemoSeedProperties.class,
                    DemoSeedRunner.class
            );

    @Test
    @DisplayName("Demo seed runner is excluded in prod profile")
    void excludedInProdProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoSeedRunner.class));
    }

    @Test
    @DisplayName("Demo seed runner is excluded in test profile")
    void excludedInTestProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test", "app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoSeedRunner.class));
    }

    @Test
    @DisplayName("Demo seed runner is excluded when app.seed.enabled is false even if dev profile is active")
    void excludedWhenFlagIsFalseInDev() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev", "app.seed.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DemoSeedRunner.class));
    }

    @Test
    @DisplayName("Demo seed runner is activated in dev profile when app.seed.enabled is true")
    void activatedInDevProfileWhenEnabled() {
        contextRunner
                .withPropertyValues("spring.profiles.active=dev", "app.seed.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DemoSeedRunner.class));
    }
}
