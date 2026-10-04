package com.flowerconnect.storage.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Upload limits and normalisation settings ({@code app.image-upload.*}).
 *
 * <p>Every bound is a property rather than a constant because each one encodes a
 * product or operational decision — how large a catalog photo may be, how many a
 * product may have, how much memory one decode may cost — and those decisions
 * belong to the deployment, not to a recompile.
 */
@Configuration
@ConfigurationProperties(prefix = "app.image-upload")
public class ImageUploadProperties {

    /**
     * Maximum accepted upload, in bytes, checked before the bytes are read into
     * memory. Must not exceed {@code spring.servlet.multipart.max-file-size},
     * which rejects the request at the container before it reaches this code.
     */
    private long maxFileSizeBytes = 5L * 1024 * 1024;

    /**
     * Maximum number of images per product. Bounds the storage and the storefront
     * query for one product; a vendor who needs more should get a larger number
     * here rather than a per-product exception.
     */
    private int maxImagesPerProduct = 8;

    /**
     * Longest edge, in pixels, a stored image may have. Larger uploads are scaled
     * down proportionally rather than rejected: a vendor photographing a bouquet
     * on a 48-megapixel phone should get a catalog image, not an error.
     */
    private int maxDimension = 1600;

    /**
     * Ceiling on decoded pixels, checked from the header before the image is
     * decoded. This is the decompression-bomb bound: a few kilobytes can claim
     * 20000×20000 and cost 1.6 GB to decode, so the size limit alone is not a
     * memory limit. Rejected rather than scaled, because an image this large is
     * never legitimate.
     */
    private long maxPixels = 40_000_000L;

    /** JPEG quality, 0..1, for re-encoded output. */
    private double jpegQuality = 0.85;

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxImagesPerProduct() {
        return maxImagesPerProduct;
    }

    public void setMaxImagesPerProduct(int maxImagesPerProduct) {
        this.maxImagesPerProduct = maxImagesPerProduct;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public void setMaxDimension(int maxDimension) {
        this.maxDimension = maxDimension;
    }

    public long getMaxPixels() {
        return maxPixels;
    }

    public void setMaxPixels(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    public double getJpegQuality() {
        return jpegQuality;
    }

    public void setJpegQuality(double jpegQuality) {
        this.jpegQuality = jpegQuality;
    }
}