package com.flowerconnect.storage.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The image formats the upload pipeline accepts (plan task 3.8: "type whitelist
 * (JPEG/PNG/WebP) checked by content sniffing").
 *
 * <p>The constant {@code mimeType} is the format of the <b>stored</b> bytes, not
 * a claim about what the client said or named. For {@link #WEBP} it is
 * {@code image/jpeg}, because WebP is accepted on the way in and re-encoded as
 * JPEG on the way out: neither the JDK nor the WebP ImageIO plugin in this build
 * can encode WebP. See {@link ImageProcessor} and docs/decisions.md (D-27).
 */
@Getter
@RequiredArgsConstructor
public enum ImageFormat {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String mimeType;
    private final String extension;

    /**
     * Format the stored bytes are encoded in. Only WebP changes: it decodes and
     * then re-encodes as JPEG, which is also why a WebP upload loses any alpha
     * channel (it is composited onto white).
     */
    public ImageFormat storageFormat() {
        return this == WEBP ? JPEG : this;
    }
}