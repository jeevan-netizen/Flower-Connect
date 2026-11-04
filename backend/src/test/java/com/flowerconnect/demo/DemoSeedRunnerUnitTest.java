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

import com.flowerconnect.storage.image.ImageFormat;
import com.flowerconnect.storage.image.ImageTypeDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DemoSeedRunnerUnitTest {

    @Test
    @DisplayName("DemoImageGenerator creates valid JPEG image with correct dimensions and magic bytes")
    void testDemoImageGenerator() throws IOException {
        MultipartFile file = DemoImageGenerator.generateFlowerImage(
                "Red Grand Gala Roses", Color.RED, Color.YELLOW);

        assertThat(file).isNotNull();
        assertThat(file.isEmpty()).isFalse();
        assertThat(file.getContentType()).isEqualTo("image/jpeg");
        assertThat(file.getOriginalFilename()).isEqualTo("red-grand-gala-roses.jpg");

        byte[] bytes = file.getBytes();
        assertThat(bytes.length).isGreaterThan(100);

        // Check magic bytes FF D8 FF
        ImageTypeDetector detector = new ImageTypeDetector();
        Optional<ImageFormat> detected = detector.detect(bytes);
        assertThat(detected).isPresent().contains(ImageFormat.JPEG);

        // Decode with ImageIO to verify width and height
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isEqualTo(400);
        assertThat(image.getHeight()).isEqualTo(400);
    }

    @Test
    @DisplayName("DemoSeedRunner skips seeding cleanly when demo password is missing")
    void testRunnerSkipsWhenPasswordMissing() {
        DemoSeedService mockService = mock(DemoSeedService.class);
        DemoSeedProperties props = new DemoSeedProperties();
        props.setDemoPassword(null);
        MockEnvironment env = new MockEnvironment();

        DemoSeedRunner runner = new DemoSeedRunner(mockService, props, env);
        runner.run(new DefaultApplicationArguments());

        verify(mockService, never()).seedAll(any(), any());
    }

    @Test
    @DisplayName("DemoSeedRunner invokes seedAll when password is provided")
    void testRunnerInvokesSeedWhenPasswordProvided() {
        DemoSeedService mockService = mock(DemoSeedService.class);
        DemoSeedProperties props = new DemoSeedProperties();
        props.setDemoPassword("SecretPass#123");
        props.setAdminEmail("admin@flowerconnect.test");
        MockEnvironment env = new MockEnvironment();

        DemoSeedRunner runner = new DemoSeedRunner(mockService, props, env);
        runner.run(new DefaultApplicationArguments());

        verify(mockService, times(1)).seedAll("SecretPass#123", "admin@flowerconnect.test");
    }
}

