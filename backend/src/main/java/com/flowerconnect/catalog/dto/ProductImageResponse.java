package com.flowerconnect.catalog.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a product image (plan task 3.2).
 *
 * <p>{@code storageKey} is the opaque key for the storage backend;
 * the bytes themselves are served by the image pipeline (plan
 * task 3.8), not by the catalog API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private Long id;
    private String storageKey;
    private String originalFilename;
    private String mimeType;
    private long fileSize;
    private int sortOrder;
    private boolean primary;
    private LocalDateTime createdAt;
}
