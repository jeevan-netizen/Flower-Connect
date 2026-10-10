package com.flowerconnect.storage.image;

/**
 * A validated, normalised image ready to be stored (plan task 3.8: "resize /
 * compress").
 *
 * @param content   the encoded bytes that must be persisted — never the client's
 *                  original bytes
 * @param mimeType  media type of {@code content}
 * @param extension file extension for {@code content}, without a dot
 * @param width     stored width in pixels
 * @param height    stored height in pixels
 */
public record ProcessedImage(byte[] content, String mimeType, String extension, int width, int height) {
}