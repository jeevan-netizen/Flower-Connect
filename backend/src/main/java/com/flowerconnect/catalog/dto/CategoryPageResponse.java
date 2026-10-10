package com.flowerconnect.catalog.dto;

import lombok.*;

import java.util.List;

/**
 * Paginated listing of categories. Mirrors {@code com.flowerconnect.geo.dto.PageResponse}
 * rather than reusing it, so the catalog slice stays self-contained and the
 * geo slice's generic is not dragged into the catalog's vocabulary.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryPageResponse {

    private List<CategoryResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}