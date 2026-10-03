package com.flowerconnect.catalog.service;

import com.flowerconnect.catalog.domain.Category;
import com.flowerconnect.catalog.dto.CategoryRequest;
import com.flowerconnect.catalog.dto.CategoryResponse;
import com.flowerconnect.catalog.mapper.CategoryMapper;
import com.flowerconnect.catalog.repository.CategoryRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.Role;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private CategoryService categoryService;

    private User adminUser;
    private Category parentCategory;
    private Category childCategory;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper,
                auditLogRepository, userRepository);

        Role adminRole = Role.builder().id(1L).name("ADMIN").build();
        adminUser = User.builder()
                .id(1L)
                .email("admin@test.com")
                .fullName("Admin User")
                .role(adminRole)
                .build();

        parentCategory = Category.builder()
                .id(1L)
                .name("Roses")
                .slug("roses")
                .displayOrder(1)
                .active(true)
                .build();

        childCategory = Category.builder()
                .id(2L)
                .name("Hybrid Tea")
                .slug("hybrid-tea")
                .displayOrder(1)
                .active(true)
                .parent(parentCategory)
                .build();
    }

    @Test
    void createCategoryGeneratesSlugAndPersists() {
        CategoryRequest request = CategoryRequest.builder()
                .name("New Category")
                .displayOrder(5)
                .active(true)
                .build();

        when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(categoryMapper.toResponse(any(Category.class)))
                .thenReturn(CategoryResponse.builder().id(10L).slug("new-category").build());

        CategoryResponse response = categoryService.create("admin@test.com", request);

        assertThat(response.getSlug()).isEqualTo("new-category");
        verify(categoryRepository).save(any(Category.class));
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void createCategoryAppendsSuffixOnSlugCollision() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Roses")
                .build();

        when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        when(categoryRepository.existsBySlug("roses")).thenReturn(true);
        when(categoryRepository.existsBySlug("roses-2")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(categoryMapper.toResponse(any(Category.class)))
                .thenReturn(CategoryResponse.builder().id(10L).slug("roses-2").build());

        CategoryResponse response = categoryService.create("admin@test.com", request);

        assertThat(response.getSlug()).isEqualTo("roses-2");
    }

    @Test
    void createCategoryWithParentSetsParent() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Child")
                .parentId(1L)
                .build();

        when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setId(10L);
            return c;
        });
        when(categoryMapper.toResponse(any(Category.class)))
                .thenReturn(CategoryResponse.builder().id(10L).parentId(1L).build());

        CategoryResponse response = categoryService.create("admin@test.com", request);

        assertThat(response.getParentId()).isEqualTo(1L);
    }

    @Test
    void createCategoryRejectsUnknownParent() {
        CategoryRequest request = CategoryRequest.builder()
                .name("Child")
                .parentId(999L)
                .build();

        lenient().when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        lenient().when(categoryRepository.existsBySlug(anyString())).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create("admin@test.com", request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Parent category not found");
    }

    @Test
    void updateCategoryRegeneratesSlugOnNameChange() {
        Category existing = Category.builder()
                .id(1L)
                .name("Old Name")
                .slug("old-name")
                .displayOrder(1)
                .active(true)
                .build();

        CategoryRequest request = CategoryRequest.builder()
                .name("New Name")
                .build();

        when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsBySlug("new-name")).thenReturn(false);
        when(categoryRepository.saveAndFlush(any(Category.class))).thenReturn(existing);
        when(categoryMapper.toResponse(any(Category.class)))
                .thenReturn(CategoryResponse.builder().id(1L).slug("new-name").build());

        CategoryResponse response = categoryService.update("admin@test.com", 1L, request);

        assertThat(response.getSlug()).isEqualTo("new-name");
    }

    @Test
    void updateCategoryPreventsCycle() {
        Category parent = Category.builder().id(2L).name("Parent").slug("parent").build();
        Category child = Category.builder()
                .id(1L)
                .name("Child")
                .slug("child")
                .parent(parent)
                .build();

        CategoryRequest request = CategoryRequest.builder()
                .name("Child")
                .parentId(1L)
                .build();

        lenient().when(categoryRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> categoryService.update("admin@test.com", 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void deleteCategoryRefusesWhenHasChildren() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.countByParentId(1L)).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.delete("admin@test.com", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("child categories");
    }

    @Test
    void deleteCategorySucceedsWhenNoChildren() {
        when(userRepository.findByEmailWithRole("admin@test.com"))
                .thenReturn(Optional.of(adminUser));
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.countByParentId(1L)).thenReturn(0L);

        categoryService.delete("admin@test.com", 1L);

        verify(categoryRepository).delete(parentCategory);
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    void listActiveReturnsFlatListOfActiveCategories() {
        when(categoryRepository.findByParentIdIsNullOrderByDisplayOrderAscIdAsc())
                .thenReturn(List.of(parentCategory, childCategory));
        when(categoryMapper.toResponse(anyList()))
                .thenReturn(List.of(
                        CategoryResponse.builder().id(1L).slug("roses").build(),
                        CategoryResponse.builder().id(2L).slug("hybrid-tea").build()));

        var response = categoryService.listActive();

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
    }
}