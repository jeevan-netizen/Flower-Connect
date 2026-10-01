package com.flowerconnect.vendor.dto;

import lombok.*;

import java.util.List;

/**
 * Page wrapper for the admin vendor listing (plan task 2.6). Mirrors the shape
 * of the Phase 2a location page response, but is declared in the vendor package
 * so the vendor feature slice does not depend on the geo slice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProfilePageResponse {

    private List<VendorProfileResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
