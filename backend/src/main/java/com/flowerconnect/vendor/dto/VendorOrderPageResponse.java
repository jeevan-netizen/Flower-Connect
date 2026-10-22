package com.flowerconnect.vendor.dto;

import com.flowerconnect.vendor.dto.VendorOrderResponse;
import lombok.*;

import java.util.List;

/**
 * Paged wrapper for the vendor's order listing (plan Phase 6, task 6.4).
 * Mirrors the shape of the other page responses in the vendor slice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorOrderPageResponse {

    private List<VendorOrderResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
