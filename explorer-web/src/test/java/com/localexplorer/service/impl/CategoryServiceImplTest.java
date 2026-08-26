package com.localexplorer.service.impl;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheTestDoubles;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.CategoryDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private CategoryServiceImpl categoryService;

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ExploreItemMapper itemMapper;
    @Mock
    private ExplorePackageMapper packageMapper;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl();
        ReflectionTestUtils.setField(categoryService, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(categoryService, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(categoryService, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(categoryService, "hotCache", HotCacheTestDoubles.passThroughCache());
        ReflectionTestUtils.setField(categoryService, "cacheInvalidationCoordinator",
                mock(CacheInvalidationCoordinator.class));
    }

    @Test
    void saveDefaultsSortToZeroAndStatusToDisabled() {
        CategoryDTO dto = new CategoryDTO();
        dto.setType(1);
        dto.setName("City Walk");

        categoryService.save(dto);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).insert(categoryCaptor.capture());
        Category category = categoryCaptor.getValue();
        assertThat(category.getType()).isEqualTo(1);
        assertThat(category.getName()).isEqualTo("City Walk");
        assertThat(category.getSort()).isZero();
        assertThat(category.getStatus()).isEqualTo(StatusConstant.DISABLE);
    }

    @Test
    void deleteByIdRejectsCategoryRelatedByItem() {
        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).build());
        when(itemMapper.countByCategoryId(5L)).thenReturn(1);

        assertThatThrownBy(() -> categoryService.deleteById(5L))
                .isInstanceOf(DeletionNotAllowedException.class)
                .hasMessageContaining(MessageConstant.CATEGORY_BE_RELATED_BY_ITEM);

        verify(categoryMapper, never()).deleteById(5L);
        verify(packageMapper, never()).countByCategoryId(5L);
    }

    @Test
    void deleteByIdRejectsCategoryRelatedByPackage() {
        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).build());
        when(itemMapper.countByCategoryId(5L)).thenReturn(0);
        when(packageMapper.countByCategoryId(5L)).thenReturn(1);

        assertThatThrownBy(() -> categoryService.deleteById(5L))
                .isInstanceOf(DeletionNotAllowedException.class)
                .hasMessageContaining(MessageConstant.CATEGORY_BE_RELATED_BY_PACKAGE);

        verify(categoryMapper, never()).deleteById(5L);
    }

    @Test
    void deleteByIdDeletesUnusedCategory() {
        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).build());
        when(itemMapper.countByCategoryId(5L)).thenReturn(0);
        when(packageMapper.countByCategoryId(5L)).thenReturn(0);

        categoryService.deleteById(5L);

        verify(categoryMapper).deleteById(5L);
    }

    @Test
    void deleteByIdRejectsMissingCategory() {
        assertThatThrownBy(() -> categoryService.deleteById(404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("分类不存在");

        verify(itemMapper, never()).countByCategoryId(404L);
        verify(categoryMapper, never()).deleteById(404L);
    }

    @Test
    void updateRejectsMissingCategory() {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(404L);
        dto.setType(1);
        dto.setName("不存在的分类");

        assertThatThrownBy(() -> categoryService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("分类不存在");

        verify(categoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsTypeChangeWhenCategoryRelatedByItem() {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(5L);
        dto.setType(2);
        dto.setName("城市套餐");

        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).type(1).build());
        when(itemMapper.countByCategoryId(5L)).thenReturn(1);

        assertThatThrownBy(() -> categoryService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("已有关联内容的分类不能修改类型");

        verify(categoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsTypeChangeWhenCategoryRelatedByPackage() {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(5L);
        dto.setType(1);
        dto.setName("城市项目");

        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).type(2).build());
        when(itemMapper.countByCategoryId(5L)).thenReturn(0);
        when(packageMapper.countByCategoryId(5L)).thenReturn(1);

        assertThatThrownBy(() -> categoryService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("已有关联内容的分类不能修改类型");

        verify(categoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopUpdatesCategoryStatus() {
        when(categoryMapper.getById(5L)).thenReturn(Category.builder().id(5L).build());

        categoryService.startOrStop(StatusConstant.ENABLE, 5L);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryMapper).update(categoryCaptor.capture());
        Category category = categoryCaptor.getValue();
        assertThat(category.getId()).isEqualTo(5L);
        assertThat(category.getStatus()).isEqualTo(StatusConstant.ENABLE);
        assertThat(category.getName()).isNull();
    }

    @Test
    void startOrStopRejectsMissingCategory() {
        assertThatThrownBy(() -> categoryService.startOrStop(StatusConstant.ENABLE, 404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("分类不存在");

        verify(categoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopRejectsInvalidStatus() {
        assertThatThrownBy(() -> categoryService.startOrStop(2, 5L))
                .isInstanceOf(BaseException.class)
                .hasMessage("状态参数只能为0或1");

        verify(categoryMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }
}
