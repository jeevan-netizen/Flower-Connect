package com.flowerconnect.customer.dto;

import lombok.*;

import java.util.List;

/**
 * Paginated listing of addresses. Mirrors
 * {@code com.flowerconnect.geo.dto.PageResponse} rather than reusing it,
 * so the customer slice stays self-contained and the geo slice's generic
 * is not dragged into the customer vocabulary — the convention
 * {@code CategoryPageResponse} established for the catalog slice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressPageResponse {

    private List<AddressResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
