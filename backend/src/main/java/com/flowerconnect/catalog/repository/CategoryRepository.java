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
 * and the code that enforces it live in one place. The two helpers below are
 * the only queries the service needs on top of CRUD.
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

    @Query("SELECT c FROM Category c WHERE c.parent IS NULL "
            + "ORDER BY c.displayOrder ASC, c.id ASC")
    List<Category> findByParentIdIsNullOrderByDisplayOrderAscIdAsc();

    Page<Category> findByParentIdIsNull(Pageable pageable);
}