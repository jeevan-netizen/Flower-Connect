package com.flowerconnect.storage.image;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Random;

/**
 * Byte-level image fixtures for the upload pipeline tests (plan task 3.8).
 *
 * <p>Real JPEG and PNG bytes are generated with {@code ImageIO} so the fixtures
 * are genuinely decodable rather than headers with a plausible prefix. WebP
 * cannot be produced that way — the build has a WebP <em>reader</em> only — so a
 * known-good 1×1 WebP is carried as a base64 constant and
 * {@code ImageProcessorTest} proves it decodes.
 *
 * <p>The attack fixtures are the point of several of these methods: a real
 * executable header, a real ZIP, and a file that carries a valid PNG signature in
 * front of a payload that is not image data at all.
 */
public final class ImageFixtures {

    /**
     * A 1×1 lossy WebP. Used as the only WebP input the suite can produce
     * without a WebP encoder; {@code ImageProcessorTest} asserts it decodes, so
     * the fixture cannot silently rot into a header-only file.
     */
    private static final String WEBP_1X1_BASE64 =
            "UklGRiIAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=";

    /** {@code MZ} plus enough of a PE header shape to look like a Windows binary. */
    private static final byte[] WINDOWS_EXECUTABLE = new byte[]{
            0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00,
            (byte) 0xB8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    private ImageFixtures() {
    }

    /** A decodable JPEG of the given size. */
    public static byte[] jpeg(int width, int height) {
        return encode(solid(width, height, false), "jpg");
    }

    /** A decodable opaque PNG. */
    public static byte[] png(int width, int height) {
        return encode(solid(width, height, false), "png");
    }

    /** A decodable PNG with a transparent channel. */
    public static byte[] pngWithAlpha(int width, int height) {
        return encode(solid(width, height, true), "png");
    }

    /** A decodable GIF — a real image format that is deliberately not accepted. */
    public static byte[] gif(int width, int height) {
        return encode(solid(width, height, false), "gif");
    }

    /**
     * A JPEG of random pixels, written at <b>maximum</b> quality. High-entropy
     * content is what makes "re-encoding compresses" a real property rather than
 * an assumption: a flat image is tiny at any quality, and the JDK's default
 * write quality is lower than the pipeline's, so a default-encoded fixture would
 * come back <em>larger</em>. Writing the source at quality 1.0 makes the
 * configured output quality the only thing that can shrink it.
 */
public static byte[] noisyJpeg(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(20261004L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }
        return encodeJpeg(image, 1.0f);
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                ImageWriteParam params = writer.getDefaultWriteParam();
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(quality);
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The 1×1 WebP constant, decoded. */
    public static byte[] webp() {
        return Base64.getDecoder().decode(WEBP_1X1_BASE64);
    }

    /**
     * A file with a well-formed WebP container header and a garbage payload: it
     * passes magic-byte sniffing and must still be rejected, which is the reason
     * the pipeline decodes rather than trusting the signature.
     */
    public static byte[] corruptWebp() {
        byte[] real = webp();
        byte[] corrupt = real.clone();
        // Keep "RIFF" ... "WEBP" intact, destroy the chunk that follows.
        for (int i = 16; i < corrupt.length; i++) {
            corrupt[i] = (byte) 0xAB;
        }
        return corrupt;
    }

    /** A PNG's eight-byte signature followed by data that is not a PNG. */
    public static byte[] fakePng() {
        byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] tail = "IHDRthis is not an image payload, only the signature is real".getBytes();
        byte[] out = new byte[header.length + tail.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(tail, 0, out, header.length, tail.length);
        return out;
    }

    /** A Windows executable. */
    public static byte[] windowsExecutable() {
        return WINDOWS_EXECUTABLE.clone();
    }

    /** A ZIP local-file header — another real format that is not an image. */
    public static byte[] zip() {
        return new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00,
                0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    }

    /** {@code %PDF-} followed by filler: a document wearing nothing at all. */
    public static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n".getBytes();
    }

    public static byte[] empty() {
        return new byte[0];
    }

    /** {@code content} padded to {@code size} bytes with filler — for size-limit tests. */
    public static byte[] padded(byte[] content, int size) {
        if (content.length >= size) {
            throw new IllegalArgumentException("content is already at least the requested size");
        }
        byte[] out = new byte[size];
        System.arraycopy(content, 0, out, 0, content.length);
        for (int i = content.length; i < size; i++) {
            out[i] = (byte) 0x41;
        }
        return out;
    }

    private static BufferedImage solid(int width, int height, boolean transparent) {
        BufferedImage image = new BufferedImage(width, height,
                transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setPaint(transparent ? new Color(0, 0, 0, 0) : new Color(255, 105, 180));
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No ImageIO writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}