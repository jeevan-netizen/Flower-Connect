package com.flowerconnect.storage.image;

import com.flowerconnect.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Decodes, validates, scales and re-encodes an uploaded image (plan task 3.8:
 * "type whitelist (JPEG/PNG/WebP) checked by content sniffing, max size, random
 * filenames, no path traversal, resize/compress").
 *
 * <h2>What "validated" means here</h2>
 * A detected magic-number match is only a candidate. This class then has to
 * <em>decode</em> the file, which is what rejects a ZIP or an executable wearing
 * a PNG header, and a truncated or corrupt file wearing a WebP header. Both
 * checks are needed: sniffing alone accepts a polyglot, decoding alone would let
 * the JDK's pluggable readers decide what is acceptable, including formats that
 * should not be in a product catalog at all.
 *
 * <h2>What "processed" means here</h2>
 * Nothing is stored as it arrived. Every accepted upload is decoded and
 * re-encoded by the server, so:
 * <ul>
 *   <li>metadata the original file carried (EXIF, GPS, camera serial, embedded
 *       thumbnails) is dropped rather than re-served later;</li>
 *   <li>the stored bytes are the bytes this pipeline produced, so the size a
 *       vendor sees and the size on disk cannot disagree;</li>
 *   <li>the extension used for the stored object is derived from the sniffed
 *       format, never from the client's filename.</li>
 * </ul>
 *
 * <h2>Resize and compress</h2>
 * Images larger than {@code max-dimension} on their longest edge are scaled down
 * proportionally; smaller ones are not upscaled. JPEG output is re-encoded at
 * {@code jpeg-quality}. WebP is decoded and stored as JPEG: neither the JDK nor
 * the WebP reader in this build can encode WebP, so keeping the format would mean
 * either shipping it undecoded (losing the pipeline's guarantees) or adding a
 * second codec. Alpha is composited onto white for that case; PNG keeps its alpha.
 */
@Slf4j
@Component
public class ImageProcessor {

    private final ImageTypeDetector typeDetector;
    private final ImageUploadProperties properties;

    public ImageProcessor(ImageTypeDetector typeDetector, ImageUploadProperties properties) {
        this.typeDetector = typeDetector;
        this.properties = properties;
    }

    /**
     * Full pipeline for one upload: sniff → probe dimensions → decode → scale →
     * encode.
     *
     * @throws BusinessException {@code 415} for a format outside the whitelist,
     *                          {@code 413} when the decoded image would exceed
     *                          the pixel ceiling, {@code 400} for an empty,
     *                          truncated or otherwise undecodable file
     */
    public ProcessedImage process(byte[] content) {
        if (content == null || content.length == 0) {
            throw BusinessException.badRequest("Uploaded file is empty");
        }
        ImageFormat sourceFormat = typeDetector.detectOrThrow(content);
        ImageFormat storageFormat = sourceFormat.storageFormat();

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw BusinessException.badRequest("Uploaded file could not be read");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                // Dimensions are read from the header before the pixels are
                // decoded, so an oversized image is refused without ever
                // allocating what it claims to need.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                requireSaneDimensions(width, height);
                requirePixelBudget(width, height);

                BufferedImage source = reader.read(0);
                if (source == null) {
                    throw unreadable();
                }
                return encode(scale(source, width, height), storageFormat);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            // A truncated file, or bytes that match a signature the decoder
            // still refuses. Either way the caller sent something that is not a
            // usable image, whatever it is named.
            throw unreadable();
        }
    }

    private void requireSaneDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw unreadable();
        }
    }

    private void requirePixelBudget(int width, int height) {
        long pixels = (long) width * (long) height;
        if (pixels > properties.getMaxPixels()) {
            throw BusinessException.payloadTooLarge(
                    "Image is too large to process: " + width + "x" + height + " pixels");
        }
    }

    /** Scales down to {@code max-dimension} on the longest edge; never upscales. */
    private BufferedImage scale(BufferedImage source, int width, int height) {
        int max = properties.getMaxDimension();
        double ratio = Math.min(1.0, (double) max / Math.max(width, height));
        if (ratio >= 1.0) {
            return source;
        }
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        // JPEG has no alpha channel, so a transparent source is composited onto
        // white rather than onto whatever the default pixel happens to be.
        int targetType = storageTypeOf(source);
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, targetType);
        Graphics2D graphics = scaled.createGraphics();
        try {
            if (targetType == BufferedImage.TYPE_INT_RGB) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        log.debug("Scaled image from {}x{} to {}x{}", width, height, targetWidth, targetHeight);
        return scaled;
    }

    /**
     * Picks the intermediate type: RGB when the source has no transparency, ARGB
     * when it does so a PNG output can keep it. JPEG output is flattened later.
     */
    private int storageTypeOf(BufferedImage source) {
        return source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    private ProcessedImage encode(BufferedImage image, ImageFormat storageFormat) {
        boolean png = storageFormat == ImageFormat.PNG;
        BufferedImage output = png
                ? image
                : flattenOntoWhite(image);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers =
                    ImageIO.getImageWritersByFormatName(png ? "png" : "jpg");
            if (!writers.hasNext()) {
                throw new StorageFormatUnavailable(png ? "png" : "jpg");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(buffer)) {
                writer.setOutput(out);
                ImageWriteParam params = writer.getDefaultWriteParam();
                if (!png) {
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality((float) properties.getJpegQuality());
                }
                writer.write(null, new IIOImage(output, null, null), params);
            } finally {
                writer.dispose();
            }
            return new ProcessedImage(buffer.toByteArray(), storageFormat.getMimeType(),
                    storageFormat.getExtension(), output.getWidth(), output.getHeight());
        } catch (IOException e) {
            throw new StorageFormatUnavailable(png ? "png" : "jpg");
        }
    }

    /** Composites any transparency onto white for formats without an alpha channel. */
    private BufferedImage flattenOntoWhite(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage flattened = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return flattened;
    }

    private BusinessException unreadable() {
        return BusinessException.badRequest("Uploaded file is not a readable image");
    }

    /**
     * No encoder for a format this build requires. Not a client error: the bytes
     * were accepted, so this is a deployment problem and must not be reported as
     * a 4xx.
     */
    static class StorageFormatUnavailable extends RuntimeException {
        StorageFormatUnavailable(String format) {
            super("No ImageIO encoder available for " + format);
        }
    }
}