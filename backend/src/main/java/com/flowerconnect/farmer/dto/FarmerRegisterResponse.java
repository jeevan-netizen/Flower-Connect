package com.flowerconnect.farmer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerRegisterResponse {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String address;
    private String phone;
}
