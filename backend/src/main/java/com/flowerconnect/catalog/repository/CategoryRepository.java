package com.flowerconnect.catalog.repository;

import com.flowerconnect.catalog.domain.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Category persistence (plan task 3.1).
 *
 * <p>Cycle detection is deliberately <em>not</em> a repository concern: the
 * ancestor chain is shallow enough to walk with plain {@code findById} calls
 * inside the service transaction, and keeping the walk there means the rule
 * and the code that enforces it live in one place. The helpers below are the
 * only queries the service needs on top of CRUD.
 *
 * <p><b>There is deliberately no unfiltered roots-only query.</b> The public
 * read ({@code GET /api/v1/categories}) must return active top-level
 * categories only (D-17), and the admin listing returns <em>every</em> row, so
 * a roots-only query had two callers with two different filters and the
 * active predicate was silently missing from one of them. The single
 * roots-scoped query below therefore carries {@code active = true} in its name,
 * {@code ORDER BY}, and body, and the admin listing paginates all rows through
 * {@link JpaRepository#findAll(org.springframework.data.domain.Pageable)}
 * instead.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Direct children of a category. Used to refuse deletion of a branch. */
    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    List<Category> findByParentId(Long parentId);

    long countByParentId(Long parentId);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId "
            + "ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findByParentIdOrderByDisplayOrderAscIdAsc(Long parentId);

    @Query("SELECT c FROM Category c WHERE c.parent.id = :parentId")
    Page<Category> findByParentId(Long parentId, Pageable pageable);

    /**
     * Active top-level categories, ordered by display_order then id (the sort
     * order {@code V7__categories.sql} documents for ties). This is the only
     * query behind the public read, and it is the only reason an inactive
     * category cannot be served there: the {@code active = true} predicate is
     * part of the method name, the query and this contract.
     */
    @Query("SELECT c FROM Category c WHERE c.parent IS NULL AND c.active = true "
            + "ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscIdAsc();
}