package com.flowerconnect.geo.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceLocationProposalResponse {

    private Long id;
    private String city;
    private String area;
    private String pincode;
    private String addressLine1;
    private String addressLine2;
    private String status;
    private String proposedByEmail;
    private String rejectionReason;
    private String createdAt;
    private String reviewedAt;
}
