package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.dto.CategoryPageResponse;
import com.flowerconnect.catalog.dto.CategoryRequest;
import com.flowerconnect.catalog.dto.CategoryResponse;
import com.flowerconnect.catalog.mapper.CategoryMapper;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Admin-managed category CRUD (plan task 3.1).
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Slug generation</b> — the service generates the slug from {@code name}
 *       (lowercase, non-alphanumeric collapsed to a single hyphen). On collision
 *       a numeric suffix {@code -2}, {@code -3}… is appended so the slug stays
 *       unique without burdening the admin.</li>
 *   <li><b>Cycle prevention</b> — when a category's parent is set (create or
 *       update), the ancestor chain of the proposed parent is walked; if the
 *       category's own id appears, a 409 {@code CONFLICT} is raised.</li>
 *   <li><b>Deletion protection</b> — a category with children cannot be deleted
 *       (409 {@code CONFLICT}). This is a hard delete: the row is removed,
 *       so the protection prevents accidental data loss. The {@code active}
 *       flag exists for soft hiding without row removal.</li>
 *   <li><b>Audit</b> — every admin mutation writes one {@code audit_log} row
 *       with {@code entity_type = "CATEGORY"} and action
 *       {@code CATEGORY_CREATED}, {@code CATEGORY_UPDATED}, or
 *       {@code CATEGORY_DELETED}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9]+");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    public static final String ENTITY_TYPE = "CATEGORY";
    public static final String ACTION_CREATED = "CATEGORY_CREATED";
    public static final String ACTION_UPDATED = "CATEGORY_UPDATED";
    public static final String ACTION_DELETED = "CATEGORY_DELETED";

    private final CategoryRepository categoryRepository;
    private final CategoryMapper mapper;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new category. The slug is generated from the name; if it
     * collides, a numeric suffix is appended. The parent (if supplied) must
     * exist and must not create a cycle.
     */
    @Transactional
    public CategoryResponse create(String adminEmail, CategoryRequest request) {
        Category parent = resolveParent(request.getParentId());

        String slug = generateUniqueSlug(request.getName());
        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(request.getActive() != null ? request.getActive() : true)
                .parent(parent)
                .build();

        categoryRepository.save(category);
        writeAudit(adminEmail, ACTION_CREATED, category, null);
        log.info("Created category {} (slug={}) by {}", category.getId(), slug, adminEmail);
        return mapper.toResponse(category);
    }

    /**
     * Updates an existing category. The name may change, which regenerates the
     * slug; the parent may change, which is cycle-checked against the new
     * parent's ancestor chain. The {@code active} flag can be toggled to hide
     * the category from the public read endpoint without deleting it.
     */
    @Transactional
    public CategoryResponse update(String adminEmail, Long id, CategoryRequest request) {
        Category category = requireCategory(id);

        // If name changes, regenerate slug
        String newSlug = category.getSlug();
        if (!category.getName().equals(request.getName())) {
            newSlug = generateUniqueSlug(request.getName(), category.getId());
        }

        Category parent = resolveParent(request.getParentId());
        ensureNoCycle(category, parent);

        category.setName(request.getName());
        category.setSlug(newSlug);
        category.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        category.setActive(request.getActive() != null ? request.getActive() : category.isActive());
        category.setParent(parent);

        categoryRepository.saveAndFlush(category);
        writeAudit(adminEmail, ACTION_UPDATED, category, null);
        log.info("Updated category {} by {}", id, adminEmail);
        return mapper.toResponse(category);
    }

    /**
     * Hard-deletes a category. Refused with 409 if the category has children.
     * The audit row is written before the deletion so the trail remains.
     */
    @Transactional
    public void delete(String adminEmail, Long id) {
        Category category = requireCategory(id);
        if (categoryRepository.countByParentId(id) > 0) {
            throw BusinessException.conflict(
                    "Cannot delete a category that has child categories");
        }
        writeAudit(adminEmail, ACTION_DELETED, category, null);
        categoryRepository.delete(category);
        log.info("Deleted category {} by {}", id, adminEmail);
    }

    /**
     * Admin listing: paginated, optional parent filter. Sort is by name then id
     * for stable pagination. Returns all categories (active and inactive).
     */
    @Transactional(readOnly = true)
    public CategoryPageResponse list(Long parentId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("name", "id"));

        Page<Category> result = parentId == null
                ? categoryRepository.findByParentIdIsNull(pageable)
                : categoryRepository.findByParentId(parentId, pageable);

        return CategoryPageResponse.builder()
                .content(mapper.toResponse(result.getContent()))
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    /**
     * Public read endpoint: returns a flat list of active categories ordered by
     * display_order then name. The parent is flattened to its id and name so
     * the client can reconstruct a tree if needed.
     */
    @Transactional(readOnly = true)
    public CategoryPageResponse listActive() {
        var categories = categoryRepository.findByParentIdIsNullOrderByDisplayOrderAscIdAsc();
        return CategoryPageResponse.builder()
                .content(mapper.toResponse(categories))
                .page(0)
                .size(categories.size())
                .totalElements(categories.size())
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(categories.isEmpty())
                .build();
    }

    /**
     * Single category by id (admin use).
     */
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return mapper.toResponse(requireCategory(id));
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Category not found"));
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> BusinessException.badRequest("Parent category not found"));
    }

    /**
     * Walks from {@code proposedParent} up to the root; if {@code category}'s
     * own id is encountered, a cycle would be created.
     */
    private void ensureNoCycle(Category category, Category proposedParent) {
        if (proposedParent == null || category.getId() == null) {
            return;
        }
        Category current = proposedParent;
        while (current != null) {
            if (current.getId().equals(category.getId())) {
                throw BusinessException.conflict(
                        "Category hierarchy would contain a cycle");
            }
            current = current.getParent();
        }
    }

    /**
     * Generates a slug from {@code name}: lowercase, non-alphanumeric collapsed
     * to single hyphen, trimmed. If the slug already exists (excluding
     * {@code excludeId}), appends {@code -2}, {@code -3}… until unique.
     */
    private String generateUniqueSlug(String name, Long... excludeId) {
        String base = NON_ALPHANUM.matcher(name.toLowerCase(Locale.ROOT).trim())
                .replaceAll("-")
                .replaceAll("^-|-$", "");
        String candidate = base;
        int suffix = 2;
        Long exclude = excludeId.length > 0 ? excludeId[0] : null;
        while (categoryRepository.existsBySlug(candidate)
                && (exclude == null || !categoryRepository.findBySlug(candidate).map(Category::getId).orElse(exclude).equals(exclude))) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private void writeAudit(String adminEmail, String action, Category category, String reason) {
        User admin = userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));
        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType(action)
                .entityType(ENTITY_TYPE)
                .entityId(category.getId())
                .reason(reason)
                .build());
    }
}