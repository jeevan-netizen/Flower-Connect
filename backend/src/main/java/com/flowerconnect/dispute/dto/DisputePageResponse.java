package com.flowerconnect.dispute.dto;

import com.flowerconnect.dispute.dto.DisputeListItem;
import lombok.*;

import java.util.List;

/**
 * Paginated dispute list response (plan Phase 8, task 8.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisputePageResponse {

    private List<DisputeListItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}