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

    /**
     * One service area.
     *
     * <p>{@code id} is the {@code service_locations.id} primary key. It is exposed so a
     * client can hand the identifier straight back to the endpoints that reference a
     * location — {@code POST /api/v1/vendors/register} and
     * {@code PUT /api/v1/vendors/profile} both take a
     * {@code serviceLocationId}. Without it a picker could display city/area/pincode
     * but could not produce a valid request, and the identifier would have to be
     * guessed, which is a data-integrity bug. The field is additive: existing consumers
     * that only read area/pincode/coordinates are unaffected.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AreaResponse {
        private Long id;
        private String area;
        private String pincode;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}