package com.flowerconnect.farmer.dto;

import lombok.*;

/**
 * The authenticated farmer's own profile: name, mobile number and address.
 * Kept separate from the shared {@code /api/v1/users/me} contract so the
 * farmer portal is scoped to the data a farmer is entitled to see.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerProfileResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private String role;
}
