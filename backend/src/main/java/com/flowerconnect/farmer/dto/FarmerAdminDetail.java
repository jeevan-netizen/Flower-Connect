package com.flowerconnect.farmer.dto;

import com.flowerconnect.domain.User;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The administrator-facing view of a single farmer: the account profile, the
 * linked florists, and a read-only ledger overview separated by vendor.
 *
 * <p>Carries no credential material. Financial figures are reported exactly as
 * the farmer passbook backend computes them; the UI never recomputes balances.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerAdminDetail {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private User.Status status;
    private LocalDateTime createdAt;
    private List<FarmerVendorLedgerOverview> linkedVendors;
}
