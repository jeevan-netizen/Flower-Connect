package com.flowerconnect.vendor.controller;

import com.flowerconnect.catalog.dto.ProductImageOrderRequest;
import com.flowerconnect.catalog.dto.ProductImageResponse;
import com.flowerconnect.catalog.service.ProductImageService;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Vendor image management for a product (plan task 3.8).
 *
 * <p>Mounted at {@code /api/v1/vendors/products/{productId}/images}, inside the
 * existing vendor namespace, so the {@code hasRole("FLORIST")} rule in
 * {@code SecurityConfig} already applies and the two authorization layers keep
 * their documented order (D-13): the namespace answers "is this a vendor
 * account?" and {@link RequiresApprovedVendor} answers "may this vendor
 * transact?". An unapproved vendor is refused image uploads with the same
 * {@code VENDOR_NOT_APPROVED} code as the catalog routes.
 *
 * <p>Upload is the only route that takes a file, and it takes it as
 * {@code multipart/form-data} part {@code file} plus an optional
 * {@code primary} flag. The declared part {@code Content-Type} and the file
 * name are ignored for the purpose of deciding what the file is — both are
 * client-supplied strings — and the rejection reasons it can produce are
 * enumerated on the operation below.
 */
@Slf4j
@Validated
@Tag(name = "Vendor images", description = "Product image upload, cover selection and ordering")
@RequiresApprovedVendor
@RestController
@RequestMapping("/api/v1/vendors/products/{productId}/images")
@RequiredArgsConstructor
public class VendorProductImageController {

    private final ProductImageService productImageService;

    @Operation(summary = "Upload a product image",
            description = "Accepts JPEG, PNG and WebP, decided by the file's content and not by its name or "
                    + "declared type. The image is decoded, scaled to fit and re-encoded by the server; the "
                    + "stored object is named by the server and its extension comes from the detected format. "
                    + "The first image of a product becomes its cover automatically. Requires an APPROVED vendor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Image stored"),
            @ApiResponse(responseCode = "400", description = "Empty file, or bytes that are not a readable image"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product"),
            @ApiResponse(responseCode = "409", description = "The product already holds the maximum number of images"),
            @ApiResponse(responseCode = "413", description = "File over the size limit, or more pixels than the decode budget"),
            @ApiResponse(responseCode = "415", description = "Content is not JPEG, PNG or WebP")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> uploadImage(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "primary", defaultValue = "false") boolean primary) {

        ProductImageResponse response = productImageService.upload(
                authentication.getName(), productId, file, primary);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List a product's images",
            description = "Returns the product's images in display order, each flagged primary or not.")
    @GetMapping
    public ResponseEntity<List<ProductImageResponse>> listImages(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId) {
        return ResponseEntity.ok(productImageService.list(authentication.getName(), productId));
    }

    @Operation(summary = "Set an image as the product cover",
            description = "Clears the previous cover in the same transaction, so exactly one image is primary "
                    + "afterwards. Returns the full ordered list because two rows changed.")
    @PutMapping("/{imageId}/primary")
    public ResponseEntity<List<ProductImageResponse>> setPrimaryImage(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @PathVariable @Positive(message = "Image id must be positive") Long imageId) {
        return ResponseEntity.ok(productImageService.setPrimary(authentication.getName(), productId, imageId));
    }

    @Operation(summary = "Reorder a product's images",
            description = "The request must list every image of the product exactly once; a partial or "
                    + "repeated list is rejected rather than interpreted.")
    @PutMapping("/order")
    public ResponseEntity<List<ProductImageResponse>> reorderImages(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody ProductImageOrderRequest request) {
        return ResponseEntity.ok(
                productImageService.reorder(authentication.getName(), productId, request.getImageIds()));
    }

    @Operation(summary = "Delete a product image",
            description = "Removes the row and the stored object. Deleting the cover promotes the next image, so "
                    + "the product is never left with images and no cover.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Image deleted"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product, or no such image of it")
    })
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> deleteImage(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @PathVariable @Positive(message = "Image id must be positive") Long imageId) {
        productImageService.delete(authentication.getName(), productId, imageId);
        return ResponseEntity.noContent().build();
    }
}