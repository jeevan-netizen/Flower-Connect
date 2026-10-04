package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.ProductImage;
import com.flowerconnect.catalog.dto.ProductImageResponse;
import com.flowerconnect.catalog.mapper.ProductMapper;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.storage.StorageService;
import com.flowerconnect.storage.image.ImageProcessor;
import com.flowerconnect.storage.image.ImageUploadProperties;
import com.flowerconnect.storage.image.ProcessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Product image upload pipeline and the one-primary invariant (plan task 3.8).
 *
 * <h2>What this service refuses</h2>
 * Every rejection below happens <b>before</b> any bytes are written, in this
 * order, so a rejected upload leaves neither a row nor a file:
 * <ol>
 *   <li>a product the caller does not own — 403, or 404 when it does not exist
 *       (shared with the catalog service, so "yours" is defined once);</li>
 *   <li>an empty part — 400;</li>
 *   <li>a part over {@code max-file-size-bytes} — 413, checked from the declared
 *       length so the bytes are never buffered;</li>
 *   <li>a format outside the whitelist, decided by content sniffing — 415;</li>
 *   <li>bytes that do not decode, or a header claiming more pixels than the
 *       budget allows — 400 / 413;</li>
 *   <li>a product already holding {@code max-images-per-product} images — 409.</li>
 * </ol>
 *
 * <h2>What this service guarantees</h2>
 * <ul>
 *   <li><b>Never the client's bytes.</b> What is stored is what
 *       {@link ImageProcessor} decoded and re-encoded, so EXIF, GPS and any other
 *       embedded metadata are dropped.</li>
 *   <li><b>Never the client's name as a path.</b> The key is
 *       {@code product-images/{productId}/{uuid}.{ext}} — a server-generated
 *       UUID under a server-generated prefix, with the extension derived from the
 *       sniffed format. The uploaded name is kept in
 *       {@code original_filename} for display, reduced to a bare basename.</li>
 *   <li><b>Exactly one primary whenever the product has images.</b> Setting a
 *       primary clears the others in the same transaction, under a
 *       {@code SELECT ... FOR UPDATE} on the product's image rows, so two
 *       concurrent requests cannot each write their own "primary". The first image
 *       of a product becomes primary automatically, and deleting the primary
 *       promotes the next one rather than leaving the product with images and no
 *       cover. See docs/decisions.md (D-28).</li>
 * </ul>
 *
 * <h2>File and row are not atomic</h2>
 * The database and the filesystem have no shared transaction. Uploads therefore
 * write the object first and delete it again if the row cannot be written;
 * deletes remove the row first and treat a failed object removal as a logged
 * orphan rather than rolling back a row that is already gone. Neither direction
 * can leave a catalog row pointing at bytes that are not there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private static final String KEY_PREFIX = "product-images";
    private static final int MAX_FILENAME_LENGTH = 255;

    /**
     * Ownership is resolved through {@link ProductService} rather than by
     * repeating the lookup: a product that is "yours" must be the same definition
     * for the catalog routes and the image routes.
     */
    private final ProductService productService;
    private final ProductImageRepository imageRepository;
    private final StorageService storageService;
    private final ImageProcessor imageProcessor;
    private final ImageUploadProperties properties;
    private final ProductMapper mapper;

    /**
     * Validates, normalises, stores and registers one uploaded image.
     *
     * @param makePrimary  whether the caller asked for this image to become the
     *                     product's cover; ignored when the product already has a
     *                     cover and this is the first image of a product, which is
     *                     always primary
     */
    @Transactional
    public ProductImageResponse upload(String vendorEmail, Long productId,
                                        MultipartFile file, boolean makePrimary) {
        Product product = productService.requireOwnedProduct(vendorEmail, productId);
        byte[] raw = readValidated(file);
        ProcessedImage processed = imageProcessor.process(raw);

        List<ProductImage> existing = lockImages(productId);
        requireImageBudget(existing);

        String key = storageKey(productId, processed.extension());
        ProductImage image = ProductImage.builder()
                .product(product)
                .storageKey(key)
                .originalFilename(sanitizeFilename(file.getOriginalFilename()))
                .mimeType(processed.mimeType())
                .fileSize(processed.content().length)
                .sortOrder(existing.size())
                .build();

        // First image of a product is the cover; otherwise only an explicit
        // request may take the cover away from the image that already holds it.
        boolean becomesPrimary = makePrimary || existing.stream().noneMatch(ProductImage::isPrimary);
        if (becomesPrimary) {
            clearPrimaries(existing);
            image.setPrimary(true);
        }

        storeAndVerify(productId, key, processed.content(), image);
        log.info("Stored image {} for product {} as {} ({} bytes stored, {}x{}, primary={})",
                image.getId(), productId, key, image.getFileSize(), processed.width(),
                processed.height(), image.isPrimary());
        return mapper.toImageResponse(image);
    }

    /** The product's images in display order. */
    @Transactional(readOnly = true)
    public List<ProductImageResponse> list(String vendorEmail, Long productId) {
        productService.requireOwnedProduct(vendorEmail, productId);
        return toResponses(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    /**
     * Removes an image and its stored bytes. Deleting the cover promotes the next
     * image so the product is never left with images and no cover; deleting the
     * last image leaves it with none, which is allowed.
     */
    @Transactional
    public void delete(String vendorEmail, Long productId, Long imageId) {
        productService.requireOwnedProduct(vendorEmail, productId);
        List<ProductImage> locked = lockImages(productId);
        ProductImage image = findImage(locked, imageId);
        boolean wasPrimary = image.isPrimary();
        String key = image.getStorageKey();

        List<ProductImage> remaining = new ArrayList<>(locked);
        remaining.remove(image);
        imageRepository.delete(image);
        imageRepository.flush();

        if (wasPrimary && !remaining.isEmpty()) {
            ProductImage promoted = remaining.get(0);
            promoted.setPrimary(true);
            imageRepository.saveAndFlush(promoted);
        }
        requireSinglePrimary(productId);

        // Row first, object second: a failed object removal leaves an unreferenced
        // file, which costs disk. The other order would leave a visible row
        // pointing at bytes that no longer exist.
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.error("Image row {} deleted but its object {} could not be removed: {}",
                    imageId, key, e.getMessage());
        }
        log.info("Deleted image {} from product {}", imageId, productId);
    }

    /**
     * Makes one image the product's cover, clearing the previous one in the same
     * transaction. Returns the full ordered list, because the caller's view is now
     * stale on two rows, not one.
     */
    @Transactional
    public List<ProductImageResponse> setPrimary(String vendorEmail, Long productId, Long imageId) {
        productService.requireOwnedProduct(vendorEmail, productId);
        List<ProductImage> locked = lockImages(productId);
        ProductImage target = findImage(locked, imageId);
        clearPrimaries(locked);
        target.setPrimary(true);
        imageRepository.saveAndFlush(target);
        requireSinglePrimary(productId);
        log.info("Image {} is now the cover of product {}", imageId, productId);
        return toResponses(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    /**
     * Rewrites the display order. The request must be a permutation of the
     * product's images: a partial list would silently leave the omitted images
     * wherever they were, and duplicated ids would produce two rows claiming one
     * position.
     */
    @Transactional
    public List<ProductImageResponse> reorder(String vendorEmail, Long productId, List<Long> imageIds) {
        productService.requireOwnedProduct(vendorEmail, productId);
        List<ProductImage> locked = lockImages(productId);
        requireExactPermutation(locked, imageIds);

        Map<Long, ProductImage> byId = new HashMap<>();
        locked.forEach(image -> byId.put(image.getId(), image));
        for (int position = 0; position < imageIds.size(); position++) {
            byId.get(imageIds.get(position)).setSortOrder(position);
        }
        imageRepository.saveAllAndFlush(locked);
        return toResponses(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId));
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Reads the part, enforcing the empty and size rules first so an oversized
     * upload is refused from its declared length instead of being buffered first.
     */
    private byte[] readValidated(MultipartFile file) {
        if (file == null) {
            throw BusinessException.badRequest("File part is required");
        }
        if (file.isEmpty()) {
            throw BusinessException.badRequest("Uploaded file is empty");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw BusinessException.payloadTooLarge("Uploaded file is larger than the maximum allowed size");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw BusinessException.badRequest("Uploaded file could not be read");
        }
    }

    private void requireImageBudget(List<ProductImage> existing) {
        if (existing.size() >= properties.getMaxImagesPerProduct()) {
            throw BusinessException.conflict("Product already has the maximum of "
                    + properties.getMaxImagesPerProduct() + " images");
        }
    }

    /**
     * A reorder must name every image exactly once. Refused rather than padded:
     * guessing where an omitted image belongs is not something the server can do
     * honestly.
     */
    private void requireExactPermutation(List<ProductImage> locked, List<Long> imageIds) {
        if (imageIds == null || imageIds.size() != locked.size()) {
            throw BusinessException.badRequest("Reorder must list every image of the product exactly once");
        }
        Set<Long> requested = new HashSet<>(imageIds);
        if (requested.size() != imageIds.size()) {
            throw BusinessException.badRequest("Reorder must not repeat an image id");
        }
        boolean matches = locked.stream().allMatch(image -> requested.contains(image.getId()));
        if (!matches) {
            throw BusinessException.badRequest("Reorder must not reference an image of another product");
        }
    }

    /**
     * Post-condition for the one-primary invariant: at most one primary, and
     * exactly one whenever the product still has images. A defensive assertion
     * rather than the mechanism — the clear-then-set above is what enforces the
     * rule — but it turns a silent violation into a failed request, and it is
     * what {@code ProductImageIntegrationTest} exercises concurrently.
     */
    private void requireSinglePrimary(Long productId) {
        long primaries = imageRepository.countByProductIdAndPrimaryIsTrue(productId);
        long images = imageRepository.countByProductId(productId);
        long expected = images == 0 ? 0 : 1;
        if (primaries != expected) {
            throw new IllegalStateException(
                    "Primary image invariant violated for product " + productId + ": " + primaries
                            + " primary image(s) across " + images + " image(s)");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private List<ProductImage> lockImages(Long productId) {
        return imageRepository.findByProductIdForUpdate(productId);
    }

    private void clearPrimaries(List<ProductImage> images) {
        images.stream().filter(ProductImage::isPrimary).forEach(image -> image.setPrimary(false));
    }

    /**
     * An image id that is not one of the product's locked images is a 404, not a
     * 403: the caller is asking for an image <em>of this product</em>, and this
     * product does not have it. Reporting 403 would imply the image exists and
     * belongs to someone else.
     */
    private ProductImage findImage(List<ProductImage> locked, Long imageId) {
        return locked.stream()
                .filter(image -> image.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Product image not found"));
    }

    /**
     * Writes the object, then the row, then checks the one-primary invariant. If
     * anything after the write fails the object is removed again: an unreferenced
     * file is invisible, whereas a row pointing at nothing is a broken image on
     * the storefront.
     */
    private void storeAndVerify(Long productId, String key, byte[] content, ProductImage image) {
        storageService.store(key, content);
        try {
            imageRepository.saveAndFlush(image);
            requireSinglePrimary(productId);
        } catch (RuntimeException e) {
            try {
                storageService.delete(key);
            } catch (RuntimeException cleanupFailure) {
                log.error("Image {} was rolled back but its object {} could not be removed: {}",
                        image.getId(), key, cleanupFailure.getMessage());
            }
            throw e;
        }
    }

    /**
     * Server-generated key. The product id scopes the objects, the UUID makes them
     * unguessable and collision-free, and the extension comes from the sniffed
     * format — never from the uploaded filename.
     */
    private String storageKey(Long productId, String extension) {
        return KEY_PREFIX + "/" + productId + "/" + UUID.randomUUID() + "." + extension.toLowerCase(Locale.ROOT);
    }

    /**
     * Reduces an uploaded filename to a bare basename for display: any directory
     * component is dropped, control characters removed, and the length clamped to
     * the column. The value is never used as a path — the storage key is
     * generated — so this is about not persisting a traversal string and not
     * handing a future renderer something unexpected.
     */
    static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String normalized = originalFilename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String base = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String cleaned = base.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return null;
        }
        return cleaned.length() > MAX_FILENAME_LENGTH ? cleaned.substring(0, MAX_FILENAME_LENGTH) : cleaned;
    }

    private List<ProductImageResponse> toResponses(List<ProductImage> images) {
        return images.stream().map(mapper::toImageResponse).toList();
    }
}