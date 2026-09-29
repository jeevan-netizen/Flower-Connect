package com.flowerconnect.geo.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceLocationResponse {

    private String city;
    private List<AreaResponse> areas;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AreaResponse {
        private String area;
        private String pincode;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}