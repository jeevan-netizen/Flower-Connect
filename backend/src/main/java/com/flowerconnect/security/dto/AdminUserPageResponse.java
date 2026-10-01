package com.flowerconnect.security.dto;

import lombok.*;

import java.util.List;

/**
 * Page wrapper for the admin user listing (plan task 2.8).
 *
 * <p>Same shape as the Phase 2a location page response and the Phase 2c admin
 * vendor page response, declared in the security package so the admin user slice
 * does not depend on the geo or vendor slices.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserPageResponse {

    private List<AdminUserResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}