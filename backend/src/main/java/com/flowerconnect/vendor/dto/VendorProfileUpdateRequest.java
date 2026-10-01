package com.flowerconnect.vendor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full replacement of the authenticated vendor's own profile and delivery
 * settings (plan task 2.5, {@code PUT /api/v1/vendors/profile}).
 *
 * <p>PUT semantics: an omitted optional scalar resets to its schema default, so
 * a client must resend every field it wants to keep. {@code hours} is the one
 * exception — it is a sub-resource, so an absent (null) list leaves the stored
 * week untouched while a present list replaces the whole week.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorProfileUpdateRequest {

    @NotBlank(message = "Business name is required")
    @Size(max = 160, message = "Business name must not exceed 160 characters")
    private String businessName;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255, message = "Address line 1 must not exceed 255 characters")
    private String addressLine1;

    @Size(max = 255, message = "Address line 2 must not exceed 255 characters")
    private String addressLine2;

    @NotNull(message = "Service location is required")
    @Positive(message = "Service location must be a positive identifier")
    private Long serviceLocationId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Delivery radius must be greater than 0")
    @Digits(integer = 3, fraction = 2, message = "Delivery radius must have at most 3 integer and 2 fraction digits")
    private BigDecimal deliveryRadiusKm;

    @Size(max = 512, message = "Logo URL must not exceed 512 characters")
    private String logoUrl;

    @DecimalMin(value = "0.0", message = "Minimum order amount must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Minimum order amount must have at most 8 integer and 2 fraction digits")
    private BigDecimal minOrderAmount;

    @DecimalMin(value = "0.0", message = "Base delivery fee must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Base delivery fee must have at most 8 integer and 2 fraction digits")
    private BigDecimal baseDeliveryFee;

    @DecimalMin(value = "0.0", message = "Per km fee must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Per km fee must have at most 8 integer and 2 fraction digits")
    private BigDecimal perKmFee;

    @DecimalMin(value = "0.0", message = "Free delivery threshold must not be negative")
    @Digits(integer = 8, fraction = 2, message = "Free delivery threshold must have at most 8 integer and 2 fraction digits")
    private BigDecimal freeDeliveryAbove;

    @Min(value = 1, message = "Prep time must be at least 1 minute")
    @Max(value = 1440, message = "Prep time must not exceed 1440 minutes")
    private Integer prepTimeMinutes;

    @Min(value = 1, message = "Slot duration must be at least 1 minute")
    @Max(value = 1440, message = "Slot duration must not exceed 1440 minutes")
    private Integer slotDurationMinutes;

    @Min(value = 1, message = "Max orders per slot must be at least 1")
    @Max(value = 10000, message = "Max orders per slot must not exceed 10000")
    private Integer maxOrdersPerSlot;

    private Boolean acceptingOrders;

    @Valid
    @Size(max = 7, message = "A vendor week cannot contain more than 7 days")
    private List<VendorHoursRequest> hours;
}
