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

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generator for local demo flower images.
 * Produces valid JPEG images programmatically without external dependencies,
 * licensed under Apache 2.0 (documented in docs/credits.md).
 */
public final class DemoImageGenerator {

    private DemoImageGenerator() {
    }

    /**
     * Generates a 400x400 flower image as a {@link MultipartFile}.
     *
     * @param productName the name of the product
     * @param petalColor  primary color for flower petals
     * @param centerColor primary color for the flower center
     * @return a valid JPEG MultipartFile
     */
    public static MultipartFile generateFlowerImage(String productName, Color petalColor, Color centerColor) {
        int width = 400;
        int height = 400;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. Background gradient (soft pastel cream/mint)
            GradientPaint bgGradient = new GradientPaint(
                    0, 0, new Color(248, 250, 248),
                    0, height, new Color(236, 244, 238)
            );
            g.setPaint(bgGradient);
            g.fillRect(0, 0, width, height);

            // 2. Green stem and leaf
            g.setColor(new Color(60, 140, 75));
            g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(200, 220, 200, 320);

            // Leaves
            g.fillOval(170, 260, 30, 16);
            g.fillOval(200, 275, 30, 16);

            // 3. Flower petals (12 petals in a circle around center (200, 180))
            int centerX = 200;
            int centerY = 180;
            int petalRadius = 45;
            int petalWidth = 26;
            int petalHeight = 55;

            AffineTransform oldTransform = g.getTransform();
            g.setColor(petalColor);

            int petalCount = 8;
            for (int i = 0; i < petalCount; i++) {
                double angle = (2 * Math.PI / petalCount) * i;
                AffineTransform transform = AffineTransform.getTranslateInstance(centerX, centerY);
                transform.rotate(angle);
                transform.translate(0, -petalRadius);
                g.setTransform(transform);
                g.fill(new Ellipse2D.Double(-petalWidth / 2.0, -petalHeight / 2.0, petalWidth, petalHeight));
            }
            g.setTransform(oldTransform);

            // 4. Central disc
            g.setColor(centerColor);
            int discRadius = 24;
            g.fillOval(centerX - discRadius, centerY - discRadius, discRadius * 2, discRadius * 2);

            // Inner disc highlight
            g.setColor(new Color(255, 255, 255, 100));
            g.fillOval(centerX - discRadius / 2, centerY - discRadius / 2, discRadius, discRadius);

            // 5. Title banner at bottom
            g.setColor(new Color(40, 50, 45));
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(productName);
            int textX = Math.max(10, (width - textWidth) / 2);
            g.drawString(productName, textX, 360);

            // Subtitle
            g.setColor(new Color(100, 120, 110));
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String sub = "FlowerConnect Demo Collection";
            int subWidth = g.getFontMetrics().stringWidth(sub);
            g.drawString(sub, (width - subWidth) / 2, 380);

        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpeg", baos);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate flower image", e);
        }

        String filename = productName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + ".jpg";
        return new InMemoryMultipartFile("file", filename, "image/jpeg", baos.toByteArray());
    }
}

