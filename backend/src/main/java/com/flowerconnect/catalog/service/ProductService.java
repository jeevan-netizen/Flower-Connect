package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductPageResponse;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.mapper.ProductMapper;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.catalog.repository.ProductImageRepository;
import com.flowerconnect.catalog.repository.ProductRepository;
import com.flowerconnect.catalog.specification.ProductSpecifications;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.inventory.domain.Inventory;
import com.flowerconnect.inventory.repository.InventoryRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Product domain service — the foundation of the catalog
 * (plan tasks 3.2–3.4) and the vendor-facing operations of task 3.5.
 *
 * <p>Four invariants are owned here:
 * <ul>
 *   <li><b>Slug uniqueness</b> — the slug is generated from the
 *       product name (lowercase, non-alphanumeric collapsed to a
 *       single hyphen) with a collision-safe numeric suffix. The
 *       existence check is only a fast path: the unique
 *       constraint on {@code products.slug} is the authority, and
 *       a create that loses a race retries with suffixed slugs
 *       after the constraint fires. The service never relies on
 *       the pre-check alone.</li>
 *   <li><b>Product ⇒ inventory</b> — creating a product creates
 *       its {@code inventory} row (quantity 0, reserved 0,
 *       threshold 0) in the <em>same transaction</em>, so a
 *       product can never exist without a stock level. No stock
 *       movement is written for the initial zero: the log records
 *       <em>changes</em>, and zero is the starting state, not a
 *       change.</li>
 *   <li><b>Ownership</b> — update, read and deactivate are scoped
 *       to the calling vendor: a foreign product is a 403, a
 *       missing one a 404. Every method resolves the vendor from
 *       the JWT subject rather than from a parameter, so no caller
 *       can name another vendor.</li>
 *   <li><b>Active category</b> — a product may only be assigned to
 *       a category that is active. A soft-deleted (deactivated)
 *       category must not become assignable through the catalog
 *       API, so the check lives here rather than only in the
 *       public category listing.</li>
 * </ul>
 *
 * <p>Removal is soft: {@link #deactivate} sets
 * {@link ProductStatus#INACTIVE} and leaves the row, its images and
 * its inventory in place, so historical stock movements stay
 * readable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
    private static final int MAX_SLUG_ATTEMPTS = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Creates a product and its inventory row in one transaction.
     * The slug is generated from the name; the inventory starts at
     * zero and no stock movement is written.
     */
    @Transactional
    public ProductResponse create(String vendorEmail, ProductRequest request) {
        VendorProfile vendor = requireVendor(vendorEmail);
        Category category = requireActiveCategory(request.getCategoryId());
        validatePrice(request.getBasePrice());
        String base = normalizeSlug(request.getName());

        Product product = persistWithUniqueSlug(vendor, category, request, base);

        // Task 3.3 invariant: exactly one inventory row per product,
        // created in this same transaction at quantity 0. No stock
        // movement is written for the initial zero — the log records
        // changes only.
        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(0)
                .reservedQuantity(0)
                .lowStockThreshold(0)
                .build());

        log.info("Created product {} (slug={}) for vendor {}",
                product.getId(), product.getSlug(), vendorEmail);
        return toResponse(product);
    }

    /**
     * Updates a product the calling vendor owns. A rename
     * regenerates the slug with the same collision-safe rule.
     */
    @Transactional
    public ProductResponse update(String vendorEmail, Long productId, ProductRequest request) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        Category category = requireActiveCategory(request.getCategoryId());
        validatePrice(request.getBasePrice());

        boolean nameChanged = !product.getName().equals(request.getName());
        String base = nameChanged ? normalizeSlug(request.getName()) : null;

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setBasePrice(request.getBasePrice());
        product.setCategory(category);
        if (request.getStatus() != null) {
            product.setStatus(request.getStatus());
        }
        if (nameChanged) {
            applyUniqueSlug(product, base);
        }

        saveWithSlugRetry(product, base);
        log.info("Updated product {} by {}", productId, vendorEmail);
        return toResponse(product);
    }

    /**
     * Soft-deletes a product the calling vendor owns by moving it to
     * {@link ProductStatus#INACTIVE}. Nothing is removed: the row, its images
     * and its inventory remain so historical stock movements stay readable, and
     * the vendor can reactivate it through {@link #update}.
     */
    @Transactional
    public void deactivate(String vendorEmail, Long productId) {
        Product product = requireOwnedProduct(vendorEmail, productId);
        product.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(product);
        log.info("Deactivated product {} by {}", productId, vendorEmail);
    }

    /**
     * Single product by id, without a vendor scope. Reserved for the
     * storefront read path, which has no authenticated vendor; every
     * vendor-facing read goes through {@link #getByIdForVendor}.
     */
    @Transactional(readOnly = true)
    public ProductResponse getById(Long productId) {
        return toResponse(productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found")));
    }

    /**
     * Single product by id, restricted to the calling vendor's catalog. A
     * product owned by another vendor is a 403 and a product that does not
     * exist is a 404, so the two cases stay distinguishable to the client.
     */
    @Transactional(readOnly = true)
    public ProductResponse getByIdForVendor(String vendorEmail, Long productId) {
        return toResponse(requireOwnedProduct(vendorEmail, productId));
    }

    /**
     * Vendor-scoped listing, newest first, page size clamped to {@code 1..100}.
     *
     * <p>{@code status}, {@code categoryId} and {@code name} are optional filters;
     * a null or blank value omits the corresponding predicate rather than
     * matching nothing. Sorting is pinned to {@code createdAt} then {@code id} so
     * pagination is stable for rows written in the same instant.
     */
    @Transactional(readOnly = true)
    public ProductPageResponse list(String vendorEmail,
                                    ProductStatus status,
                                    Long categoryId,
                                    String name,
                                    int page,
                                    int size) {
        VendorProfile vendor = requireVendor(vendorEmail);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(
                safePage, safeSize,
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));

        Specification<Product> spec = ProductSpecifications.forVendor(vendor.getId());
        if (status != null) {
            spec = spec.and(ProductSpecifications.withStatus(status));
        }
        if (categoryId != null) {
            spec = spec.and(ProductSpecifications.inCategory(categoryId));
        }
        if (name != null && !name.isBlank()) {
            spec = spec.and(ProductSpecifications.nameContains(name));
        }

        Page<Product> result = productRepository.findAll(spec, pageable);

        return ProductPageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    // ------------------------------------------------------------------
    // Slug generation
    // ------------------------------------------------------------------

    /**
     * Persists a new product under a unique slug. The existence
     * check is a fast path only: if a concurrent transaction
     * claims the slug between the check and the insert, the unique
     * constraint fires and the insert is retried with suffixed
     * slugs until one is free.
     */
    private Product persistWithUniqueSlug(VendorProfile vendor, Category category,
                                          ProductRequest request, String base) {
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        try {
            return productRepository.saveAndFlush(newProduct(vendor, category, request, candidate));
        } catch (DataIntegrityViolationException e) {
            // Lost a race: another transaction claimed the slug between
            // the existence check and the insert. The constraint is the
            // authority, so retry with suffixed slugs.
            return retryWithSuffix(vendor, category, request, base, suffix);
        }
    }

    /**
     * Retries a slugged insert after a unique-constraint failure.
     * Each attempt clears the persistence context first: the failed
     * insert left the rejected entity in it, and a fresh entity
     * must be persisted for the next slug.
     */
    private Product retryWithSuffix(VendorProfile vendor, Category category,
                                    ProductRequest request, String base, int firstSuffix) {
        for (int suffix = firstSuffix; suffix < firstSuffix + MAX_SLUG_ATTEMPTS; suffix++) {
            entityManager.clear();
            String candidate = base + "-" + suffix;
            try {
                return productRepository.saveAndFlush(newProduct(vendor, category, request, candidate));
            } catch (DataIntegrityViolationException ignored) {
                // slug still taken; try the next suffix
            }
        }
        throw BusinessException.conflict("Unable to generate a unique slug for product");
    }

    /**
     * Sets a unique slug on an existing product being renamed,
     * ignoring the product's own current slug.
     */
    private void applyUniqueSlug(Product product, String base) {
        String candidate = base;
        int suffix = 2;
        while (productRepository.existsBySlugAndIdNot(candidate, product.getId())) {
            candidate = base + "-" + suffix++;
        }
        product.setSlug(candidate);
    }

    /**
     * Saves an updated product, retrying with suffixed slugs if a
     * concurrent rename claimed the generated slug first. Unlike
     * create, the entity is already persistent, so no persistence
     * context clear is needed between attempts.
     */
    private void saveWithSlugRetry(Product product, String slugBase) {
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            if (slugBase == null) {
                throw e; // not a slug race — rethrow
            }
            for (int suffix = 2; suffix <= MAX_SLUG_ATTEMPTS; suffix++) {
                product.setSlug(slugBase + "-" + suffix);
                try {
                    productRepository.saveAndFlush(product);
                    return;
                } catch (DataIntegrityViolationException ignored) {
                    // slug still taken; try the next suffix
                }
            }
            throw BusinessException.conflict("Unable to generate a unique slug for product");
        }
    }

    /**
     * Normalizes a product name into a slug: lowercase, non-
     * alphanumeric collapsed to a single hyphen, trimmed. A name
     * that normalizes to nothing (all punctuation) falls back to
     * {@code "product"} so the slug is never empty.
     */
    private String normalizeSlug(String name) {
        String slug = NON_ALPHANUM.matcher(name.toLowerCase(Locale.ROOT).trim())
                .replaceAll("-")
                .replaceAll("^-|-$", "");
        return slug.isEmpty() ? "product" : slug;
    }

    private Product newProduct(VendorProfile vendor, Category category,
                               ProductRequest request, String slug) {
        return Product.builder()
                .vendor(vendor)
                .category(category)
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT)
                .build();
    }

    // ------------------------------------------------------------------
    // Lookups and ownership
    // ------------------------------------------------------------------

    private VendorProfile requireVendor(String vendorEmail) {
        return vendorProfileRepository.findByUserEmail(vendorEmail)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
    }

    private Category requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw BusinessException.badRequest("Category is required");
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.badRequest("Category not found"));
    }

    /**
     * Resolves a category for assignment to a product, refusing a deactivated
     * one. Without this a vendor could keep filing products under a category
     * the storefront no longer lists.
     */
    private Category requireActiveCategory(Long categoryId) {
        Category category = requireCategory(categoryId);
        if (!category.isActive()) {
            throw BusinessException.badRequest("Category is not active");
        }
        return category;
    }

    /**
     * A price of zero or less is rejected in the service as well as in
     * {@code ProductRequest}, so the rule holds for any writer rather than only
     * for HTTP callers (D-12).
     */
    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.badRequest("Base price must be greater than zero");
        }
    }

    private Product requireOwnedProduct(String vendorEmail, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> BusinessException.notFound("Product not found"));
        VendorProfile vendor = requireVendor(vendorEmail);
        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("Product does not belong to this vendor");
        }
        return product;
    }

    private ProductResponse toResponse(Product product) {
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElse(null);
        return mapper.toFullResponse(
                product, inventory,
                productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId()));
    }
}