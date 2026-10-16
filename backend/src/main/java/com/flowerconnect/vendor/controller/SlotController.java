package com.flowerconnect.vendor.controller;

import com.flowerconnect.vendor.dto.SlotResponse;
import com.flowerconnect.vendor.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
@Tag(name = "Vendor slots", description = "Slot discovery and availability")
public class SlotController {

    private final SlotService slotService;

    @Operation(summary = "Get available slots for a vendor",
            description = "Returns available slot windows for a vendor on a given date at a given address. " +
                    "Endpoint is restricted to CUSTOMER role and validates address ownership.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of available slots"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account or vendor not approved"),
            @ApiResponse(responseCode = "404", description = "Vendor or address not found")
    })
    @GetMapping("/{vendorId}/slots")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(
            @PathVariable @NotNull @Positive(message = "Vendor ID must be positive") Long vendorId,
            @RequestParam @NotNull @Positive(message = "Address ID must be positive") Long addressId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
    
        String email = authentication.getName();
        List<SlotResponse> slots = slotService.getAvailableSlots(vendorId, addressId, date, email);
        return ResponseEntity.ok(slots);
    }
}

