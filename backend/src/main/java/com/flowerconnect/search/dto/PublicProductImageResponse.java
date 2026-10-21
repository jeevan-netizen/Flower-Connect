package com.flowerconnect.search.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Public product image read model.
 *
 * <p>{@code storageKey} is deliberately omitted: it is an opaque storage-backend
 * key that must not be exposed to customers while no public image-serving route
 * exists (known issue 020 / D-31).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicProductImageResponse {

    private Long id;
    private String originalFilename;
    private String mimeType;
    private long fileSize;
    private int sortOrder;
    private boolean primary;
}
