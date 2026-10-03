package com.flowerconnect.catalog.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Read model for a category. The parent is flattened to its id so the client
 * can address a category without a second request; the parent name is
 * available in the hierarchy endpoint, which is what renders a tree.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String parentName;
    private String name;
    private String slug;
    private int displayOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}