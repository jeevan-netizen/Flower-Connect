package com.flowerconnect.customer.controller;

import com.flowerconnect.customer.dto.AddressPageResponse;
import com.flowerconnect.customer.dto.AddressRequest;
import com.flowerconnect.customer.dto.AddressResponse;
import com.flowerconnect.customer.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Customer address book (plan task 4.1), at {@code /api/v1/addresses}.
 *
 * <p>The namespace is restricted to the {@code CUSTOMER} role in
 * {@code SecurityConfig}: a florist or admin account is refused with
 * 403 before the request reaches this controller. The caller is
 * resolved from the JWT subject on every call, so no route accepts a
 * customer identifier and cross-customer access is not expressible in
 * the request at all — an address that belongs to another customer is
 * a 404, indistinguishable from one that does not exist.
 *
 * <p>{@code PUT} is a full replacement of the editable fields (the
 * D-15 precedent), not a patch: an omitted {@code line2} clears it.
 * The one field that keeps its stored value when omitted is
 * {@code defaultAddress}, so editing the street name cannot change which
 * address is the default.
 */
@Slf4j
@Validated
@Tag(name = "Customer address book", description = "Address CRUD for the authenticated customer")
@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "List the caller's addresses",
            description = "Returns one page of the authenticated customer's own addresses, "
                    + "the default address first and then in ascending id order. Page size "
                    + "is clamped to 1..100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's address page"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account")
    })
    @GetMapping
    public ResponseEntity<AddressPageResponse> listAddresses(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(addressService.list(authentication.getName(), page, size));
    }

    @Operation(summary = "Create an address",
            description = "Creates an address for the authenticated customer. The coordinates are "
                    + "copied from the selected service location's centroid on the server and "
                    + "cannot be supplied by the client. The first address for a customer "
                    + "becomes the default automatically; a later address is default only when "
                    + "the request explicitly says so.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Address created"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the service location is unknown"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account")
    })
    @PostMapping
    public ResponseEntity<AddressResponse> createAddress(
            Authentication authentication,
            @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.create(authentication.getName(), request);
        log.info("Created address {} for customer {}", response.getId(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Read one of the caller's addresses",
            description = "Returns a single address owned by the authenticated customer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "No such address, or it belongs to another customer")
    })
    @GetMapping("/{id}")
    public ResponseEntity<AddressResponse> getAddress(
            Authentication authentication,
            @PathVariable @Positive(message = "Address id must be positive") Long id) {
        return ResponseEntity.ok(addressService.getById(authentication.getName(), id));
    }

    @Operation(summary = "Replace an address",
            description = "Full replacement of an address the caller owns (the D-15 precedent): "
                    + "every editable field is replaced from the request, an omitted line 2 "
                    + "clears it, and changing the service location recopies the new location's "
                    + "centroid. An omitted defaultAddress keeps the stored flag; an explicit value "
                    + "is applied, and setting a new default clears the previous one.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Address updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the service location is unknown"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "No such address, or it belongs to another customer")
    })
    @PutMapping("/{id}")
    public ResponseEntity<AddressResponse> updateAddress(
            Authentication authentication,
            @PathVariable @Positive(message = "Address id must be positive") Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(addressService.update(authentication.getName(), id, request));
    }

    @Operation(summary = "Delete an address",
            description = "Deletes an address the caller owns. Deleting the default address "
                    + "promotes the oldest remaining address; deleting the only address leaves "
                    + "the customer with no default.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Address deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "No such address, or it belongs to another customer")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(
            Authentication authentication,
            @PathVariable @Positive(message = "Address id must be positive") Long id) {
        addressService.delete(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
