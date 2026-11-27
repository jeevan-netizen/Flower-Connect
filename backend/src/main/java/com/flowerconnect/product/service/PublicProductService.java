package com.flowerconnect.product.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.geo.service.GeoService;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.search.dto.PublicProductImageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Public product detail (plan task 4.9 backend).
 *
 * <p>Requires a valid {@code locationId} and applies the discovery/search
 * eligibility policy: {@code ACTIVE} product, available stock, approved and
 * accepting vendor, and within the vendor's delivery radius. Unknown or
 * ineligible products return a non-disclosing {@code 404}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductService {

    private final ServiceLocationRepository serviceLocationRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final GeoService geoService;
    private final StorageService storageService;

    /**
     * Returns public product detail for the given location.
     *
     * @throws BusinessException {@code VALIDATION_FAILED} when {@code locationId}
     *         is missing or unknown; {@code NOT_FOUND} (non-disclosing) when the
     *         product is unknown or ineligible
     */
    public PublicProductResponse productDetail(Long productId, Long locationId) {
        if (locationId == null) {
            throw BusinessException.badRequest("locationId is required");
        }

        ServiceLocation origin = serviceLocationRepository.findById(locationId)
                .orElseThrow(() -> BusinessException.badRequest("Unknown service location"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw BusinessException.notFound("Product not found");
        }

        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        if (inventory.getAvailable() <= 0) {
            throw BusinessException.notFound("Product not found");
        }

        VendorProfile vendor = product.getVendor();
        if (vendor.getStatus() != VendorProfile.Status.APPROVED || !vendor.isAcceptingOrders()) {
            throw BusinessException.notFound("Product not found");
        }

        BigDecimal distance = geoService.haversineKm(
                origin.getLatitude(), origin.getLongitude(),
                vendor.getLatitude(), vendor.getLongitude());
        if (distance.compareTo(vendor.getDeliveryRadiusKm()) > 0) {
            throw BusinessException.notFound("Product not found");
        }

        return toResponse(product, inventory);
    }

    /**
     * Returns the stored bytes for one image of a publicly eligible product.
     *
     * <p>The same visibility policy as {@link #productDetail} applies: an
     * {@code ACTIVE} product, available stock, an approved and accepting vendor,
     * and delivery within the vendor's radius. Unknown or ineligible products,
     * and images that do not belong to the product, are all a non-disclosing
     * {@code 404}. A missing storage object (the row exists but the bytes do
     * not) is also a 404, never a 500 that reveals a storage path.
     *
     * @throws BusinessException {@code VALIDATION_FAILED} when {@code locationId}
     *         is missing or unknown; {@code NOT_FOUND} for any ineligibility
     */
    public ImageData productImage(Long productId, Long imageId, Long locationId) {
        if (locationId == null) {
            throw BusinessException.badRequest("locationId is required");
        }

        ServiceLocation origin = serviceLocationRepository.findById(locationId)
                .orElseThrow(() -> BusinessException.badRequest("Unknown service location"));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw BusinessException.notFound("Product not found");
        }

        Inventory inventory = inventoryRepository.findByProductId(product.getId())
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        if (inventory.getAvailable() <= 0) {
            throw BusinessException.notFound("Product not found");
        }

        VendorProfile vendor = product.getVendor();
        if (vendor.getStatus() != VendorProfile.Status.APPROVED || !vendor.isAcceptingOrders()) {
            throw BusinessException.notFound("Product not found");
        }

        BigDecimal distance = geoService.haversineKm(
                origin.getLatitude(), origin.getLongitude(),
                vendor.getLatitude(), vendor.getLongitude());
        if (distance.compareTo(vendor.getDeliveryRadiusKm()) > 0) {
            throw BusinessException.notFound("Product not found");
        }

        com.flowerconnect.catalog.domain.ProductImage image = productImageRepository
                .findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));

        Optional<byte[]> content = storageService.read(image.getStorageKey());
        if (content.isEmpty()) {
            throw BusinessException.notFound("Product not found");
        }

        return new ImageData(content.get(), image.getMimeType());
    }

    /**
     * The stored bytes of one image and the MIME type recorded when it was
     * uploaded (validated by content sniffing at upload time, so it is safe to
     * serve as the response content type).
     */
    public record ImageData(byte[] content, String mimeType) {
    }

    private PublicProductResponse toResponse(Product product, Inventory inventory) {
        List<PublicProductImageResponse> images = productImageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream()
                .map(image -> PublicProductImageResponse.builder()
                        .id(image.getId())
                        .originalFilename(image.getOriginalFilename())
                        .mimeType(image.getMimeType())
                        .fileSize(image.getFileSize())
                        .sortOrder(image.getSortOrder())
                        .primary(image.isPrimary())
                        .build())
                .toList();

        return PublicProductResponse.builder()
                .id(product.getId())
                .vendorId(product.getVendor().getId())
                .vendorName(product.getVendor().getBusinessName())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .available(inventory.getAvailable())
                .images(images)
                .build();
    }
}
