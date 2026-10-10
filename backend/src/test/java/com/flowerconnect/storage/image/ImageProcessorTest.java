package com.flowerconnect.storage.image;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decoding, resizing and re-encoding (plan task 3.8: "resize/compress").
 *
 * <p>The assertions here are about the <b>output</b>: that what comes back is a
 * decodable image of the expected format and dimensions, that the client's bytes
 * are not what is stored, and that each rejection carries its own error code.
 */
class ImageProcessorTest {

    private final ImageTypeDetector detector = new ImageTypeDetector();

    private ImageProcessor processor() {
        return new ImageProcessor(detector, new ImageUploadProperties());
    }

    private ImageProcessor processor(ImageUploadProperties properties) {
        return new ImageProcessor(detector, properties);
    }

    // ------------------------------------------------------------------
    // Accepted formats
    // ------------------------------------------------------------------

    @Test
    void aJpegIsStoredAsJpegAtItsOwnSize() throws IOException {
        ProcessedImage processed = processor().process(ImageFixtures.jpeg(64, 48));

        assertEquals("image/jpeg", processed.mimeType());
        assertEquals("jpg", processed.extension());
        assertEquals(64, processed.width());
        assertEquals(48, processed.height());
        assertEquals(64, decodedWidth(processed.content()));
    }

    @Test
    void aPngIsStoredAsPngAndKeepsItsTransparency() throws IOException {
        ProcessedImage processed = processor().process(ImageFixtures.pngWithAlpha(32, 32));

        assertEquals("image/png", processed.mimeType());
        assertEquals("png", processed.extension());
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(processed.content()));
        assertTrue(stored.getColorModel().hasAlpha(), "PNG output must keep an alpha channel");
        assertEquals(0, stored.getRGB(0, 0),
                "a transparent source must not gain an opaque background");
    }

    @Test
    void aWebpIsAcceptedAndReEncodedAsJpeg() throws IOException {
        // The build has a WebP reader but no WebP encoder, so a WebP upload is
        // stored as JPEG (ImageFormat.storageFormat). Asserting the transcode is
        // what stops it drifting into a silent format change.
        ProcessedImage processed = processor().process(ImageFixtures.webp());

        assertEquals(ImageFormat.JPEG, ImageFormat.WEBP.storageFormat());
        assertEquals("image/jpeg", processed.mimeType());
        assertEquals("jpg", processed.extension());
        assertEquals(1, processed.width());
        assertEquals(1, processed.height());
        assertEquals(ImageFormat.JPEG, detector.detect(processed.content()).orElseThrow(),
                "the stored bytes must themselves be a JPEG");
    }

    // ------------------------------------------------------------------
    // Re-encoding: the client's bytes are never stored
    // ------------------------------------------------------------------

    @Test
    void theStoredBytesAreReEncodedNotTheUploadedBytes() {
        byte[] uploaded = ImageFixtures.noisyJpeg(200, 100);
        ProcessedImage processed = processor().process(uploaded);

        assertFalse(Arrays.equals(uploaded, processed.content()),
                "stored bytes must be the pipeline's output, not the client's file");
        assertTrue(processed.content().length < uploaded.length,
                "re-encoding high-entropy content at the configured quality must compress it");
    }

    // ------------------------------------------------------------------
    // Resize
    // ------------------------------------------------------------------

    @Test
    void anImageLongerThanTheMaximumEdgeIsScaledDownProportionally() throws IOException {
        ImageUploadProperties properties = new ImageUploadProperties();
        properties.setMaxDimension(100);

        ProcessedImage processed = processor(properties).process(ImageFixtures.jpeg(400, 200));

        assertEquals(100, processed.width());
        assertEquals(50, processed.height());
        assertEquals(100, decodedWidth(processed.content()));
    }

    @Test
    void anImageInsideTheBoundIsNotUpscaled() {
        ImageUploadProperties properties = new ImageUploadProperties();
        properties.setMaxDimension(1000);

        ProcessedImage processed = processor(properties).process(ImageFixtures.jpeg(40, 20));

        assertEquals(40, processed.width());
        assertEquals(20, processed.height());
    }

    @Test
    void aSinglePixelImageSurvivesScaling() {
        // Rounding a 2x2 image down to maxDimension 1 lands on exactly one
        // pixel; the floor exists so the encoder is never handed a zero.
        ImageUploadProperties properties = new ImageUploadProperties();
        properties.setMaxDimension(1);

        ProcessedImage processed = processor(properties).process(ImageFixtures.jpeg(2, 2));

        assertEquals(1, processed.width());
        assertEquals(1, processed.height());
    }

    // ------------------------------------------------------------------
    // Rejections
    // ------------------------------------------------------------------

    @Test
    void anUnrecognisedFormatIsRejectedWith415() {
        assertCode(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                () -> processor().process(ImageFixtures.windowsExecutable()));
        assertCode(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                () -> processor().process(ImageFixtures.zip()));
        assertCode(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                () -> processor().process(ImageFixtures.pdf()));
    }

    @Test
    void bytesThatAreNotAReadableImageAreRejectedWith400() {
        // Correct signature, unusable content: the reason the pipeline decodes.
        assertCode(ErrorCode.VALIDATION_FAILED, () -> processor().process(ImageFixtures.fakePng()));
        assertCode(ErrorCode.VALIDATION_FAILED, () -> processor().process(ImageFixtures.corruptWebp()));
        // A real image truncated to a third of its bytes: the header survives,
        // the pixel data does not.
        assertCode(ErrorCode.VALIDATION_FAILED, () -> processor().process(
                java.util.Arrays.copyOf(ImageFixtures.jpeg(64, 64), 200)));
    }

    @Test
    void anEmptyUploadIsRejectedWith400() {
        assertCode(ErrorCode.VALIDATION_FAILED, () -> processor().process(ImageFixtures.empty()));
        assertCode(ErrorCode.VALIDATION_FAILED, () -> processor().process(null));
    }

    @Test
    void aHeaderClaimingTooManyPixelsIsRejectedWith413BeforeItIsDecoded() {
        ImageUploadProperties properties = new ImageUploadProperties();
        properties.setMaxPixels(1000);

        // 400x400 = 160,000 pixels, over the 1,000 budget. A 4-byte-per-pixel
        // decode of that would be 640 KB from a file far smaller than this
        // rejection's message suggests, which is why the check reads the header.
        BusinessException thrown = assertThrows(BusinessException.class,
                () -> processor(properties).process(ImageFixtures.jpeg(400, 400)));
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, thrown.getErrorCode());
        assertTrue(thrown.getMessage().contains("400x400"));
    }

    // ------------------------------------------------------------------

    private void assertCode(ErrorCode expected, Runnable call) {
        BusinessException thrown = assertThrows(BusinessException.class, call::run);
        assertEquals(expected, thrown.getErrorCode(), () -> "wrong error code: " + thrown.getMessage());
    }

    private int decodedWidth(byte[] content) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
        assertTrue(image != null, "stored bytes must decode");
        return image.getWidth();
    }
}