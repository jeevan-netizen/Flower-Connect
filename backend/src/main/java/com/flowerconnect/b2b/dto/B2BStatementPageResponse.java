package com.flowerconnect.b2b.dto;

import com.flowerconnect.b2b.domain.B2BStatement.Status;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Paginated page of B2B statements.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class B2BStatementPageResponse {

    private List<B2BStatementResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}
