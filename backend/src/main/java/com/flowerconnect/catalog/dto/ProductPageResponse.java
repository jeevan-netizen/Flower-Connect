package com.flowerconnect.catalog.dto;

import lombok.*;

import java.util.List;

/**
 * Paginated product list (plan task 3.2).
 *
 * <p>Mirrors {@code CategoryPageResponse}: the page envelope is
 * repeated per endpoint so each response is self-describing and
 * the client never has to infer pagination metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductPageResponse {

    private List<ProductResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
