package com.flowerconnect.farmer.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One linked florist's ledger overview on a farmer's detail page.
 *
 * <p>`entries` reuse {@link PassbookEntryResponse} so the deposit/collection
 * rows and their running balances are identical to what the farmer and vendor
 * passbooks show.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerVendorLedgerOverview {

    private Long vendorId;
    private String vendorName;
    private String linkStatus;
    private LocalDateTime linkCreatedAt;
    private BigDecimal outstandingBalance;
    private List<PassbookEntryResponse> entries;
}
