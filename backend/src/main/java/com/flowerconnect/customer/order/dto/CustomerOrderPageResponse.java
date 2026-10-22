package com.flowerconnect.customer.order.dto;

import com.flowerconnect.customer.order.dto.CustomerOrderListItem;
import lombok.*;

import java.util.List;

/**
 * Paginated wrapper for the customer's order listing (plan Phase 6,
 * task 6.3).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerOrderPageResponse {

    private List<CustomerOrderListItem> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
