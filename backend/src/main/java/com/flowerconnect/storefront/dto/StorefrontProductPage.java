package com.flowerconnect.storefront.dto;

import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.geo.dto.PageResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Paginated product list embedded in a storefront response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontProductPage {

    private List<PublicProductResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;

    public static StorefrontProductPage from(PageResponse<PublicProductResponse> page) {
        return StorefrontProductPage.builder()
                .content(page.getContent())
                .page(page.getPage())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
