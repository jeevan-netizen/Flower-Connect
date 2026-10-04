package com.flowerconnect.inventory.dto;

import lombok.*;

import java.util.List;

/**
 * Paginated stock movement history for one product (plan task 3.6).
 *
 * <p>Repeats the {@code ProductPageResponse} envelope rather than
 * reusing it, for the same reason: each list response is
 * self-describing and the client never has to infer pagination
 * metadata from the row type.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovementPageResponse {

    private List<StockMovementResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}