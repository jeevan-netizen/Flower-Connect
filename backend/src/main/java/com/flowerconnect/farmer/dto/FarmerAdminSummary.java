package com.flowerconnect.farmer.dto;

import com.flowerconnect.domain.User;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A farmer as seen by an administrator in the farmer list.
 *
 * <p>Carries only the fields the admin list needs to display and act on.
 * No credential material is exposed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FarmerAdminSummary {

    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private User.Status status;
    private LocalDateTime createdAt;
}
