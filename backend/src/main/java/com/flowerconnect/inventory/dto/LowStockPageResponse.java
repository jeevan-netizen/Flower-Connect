package com.flowerconnect.inventory.dto;

import lombok.*;

import java.util.List;

/**
 * Paginated vendor-scoped low-stock list (plan task 3.6).
 *
 * <p>Same envelope as {@code ProductPageResponse} and
 * {@code StockMovementPageResponse}, repeated per endpoint so each
 * list response carries its own pagination metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LowStockPageResponse {

    private List<LowStockProductResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}