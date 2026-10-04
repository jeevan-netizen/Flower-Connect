package com.flowerconnect.storage.image;

import com.flowerconnect.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Content sniffing for uploaded images (plan task 3.8).
 *
 * <p>Neither the filename nor the request's {@code Content-Type} is consulted,
 * and neither is trusted if it is: a multipart part's declared type is a string
 * the client chose. Only the leading bytes of the file decide the format, so
 * {@code malware.exe} renamed to {@code rose.jpg} and sent as
 * {@code Content-Type: image/jpeg} are classified as what they are — an
 * unrecognised format — rather than as the image the caller claimed.
 *
 * <p>Magic bytes are a necessary filter, not a sufficient one: a polyglot can
 * carry a valid PNG header and a hostile payload. What makes acceptance safe is
 * that a detected format is only a candidate — {@link ImageProcessor} still has to
 * decode the file completely.
 *
 * <p>Signatures:
 * <ul>
 *   <li>JPEG — {@code FF D8 FF}, the SOI marker plus the first marker byte</li>
 *   <li>PNG — {@code 89 50 4E 47 0D 0A 1A 0A}, eight bytes, no ambiguity</li>
 *   <li>WebP — {@code RIFF....WEBP}: an 8-byte RIFF header, a 4-byte little-endian
 *       payload length, then the {@code WEBP} form type at offset 8. A plain RIFF
 *       container ({@code .wav}, {@code .avi}) is not WebP and is rejected.</li>
 * </ul>
 */
@Component
public class ImageTypeDetector {

    static final int HEADER_BYTES = 16;

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_FORM_TYPE = {'W', 'E', 'B', 'P'};

    /** The detected format, or empty when the content is not an accepted image. */
    public Optional<ImageFormat> detect(byte[] content) {
        if (content == null || content.length < HEADER_BYTES) {
            return Optional.empty();
        }
        if (startsWith(content, JPEG_SIGNATURE)) {
            return Optional.of(ImageFormat.JPEG);
        }
        if (startsWith(content, PNG_SIGNATURE)) {
            return Optional.of(ImageFormat.PNG);
        }
        if (startsWith(content, RIFF_SIGNATURE) && matchesAt(content, 8, WEBP_FORM_TYPE)) {
            return Optional.of(ImageFormat.WEBP);
        }
        return Optional.empty();
    }

    /**
     * Detection with the rejection attached: an unrecognised format is
     * {@code 415 UNSUPPORTED_MEDIA_TYPE} rather than a 400, because the request
     * is well-formed JSON/form data carrying a payload of a media type this
     * endpoint does not serve. A dedicated error code also lets a client
     * distinguish "wrong format" from "this file is broken".
     */
    public ImageFormat detectOrThrow(byte[] content) {
        return detect(content).orElseThrow(() -> BusinessException.unsupportedMediaType(
                "Unsupported image format: only JPEG, PNG and WebP are accepted"));
    }

    private boolean startsWith(byte[] content, byte[] signature) {
        return matchesAt(content, 0, signature);
    }

    private boolean matchesAt(byte[] content, int offset, byte[] signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (content[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}