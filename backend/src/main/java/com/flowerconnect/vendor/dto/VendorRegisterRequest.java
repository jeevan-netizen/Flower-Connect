package com.flowerconnect.vendor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Vendor registration (plan task 2.5): creates the FLORIST user account and the
 * vendor profile in one transaction, with the profile in
 * {@code PENDING_APPROVAL}.
 *
 * <p>Fields that the schema declares {@code NOT NULL DEFAULT ...} (radius, fees,
 * prep time, slot length, orders per slot, accept-orders) are optional here and
 * fall back to the schema default when omitted. Fields the schema declares
 * {@code NOT NULL} with no default are required.
 *
 * <p>{@code reviewCount}, {@code avgRating}, {@code commissionRate} and
 * {@code status} are intentionally absent: they are platform-owned.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorRegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 128, message = "Full name must not exceed 128 characters")
    private String fullName;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be a valid phone number")
    private String phone;

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
