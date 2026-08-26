package com.localexplorer.service.impl;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheTestDoubles;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreItemTagMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ExploreItemServiceImplTest {

    private ExploreItemServiceImpl itemService;

    @Mock
    private ExploreItemMapper itemMapper;
    @Mock
    private ExploreItemTagMapper itemTagMapper;
    @Mock
    private ExplorePackageItemMapper packageItemMapper;
    @Mock
    private ExploreOrderMapper orderMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        itemService = new ExploreItemServiceImpl();
        ReflectionTestUtils.setField(itemService, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(itemService, "itemTagMapper", itemTagMapper);
        ReflectionTestUtils.setField(itemService, "packageItemMapper", packageItemMapper);
        ReflectionTestUtils.setField(itemService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(itemService, "reviewMapper", reviewMapper);
        ReflectionTestUtils.setField(itemService, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(itemService, "hotCache", HotCacheTestDoubles.passThroughCache());
        ReflectionTestUtils.setField(itemService, "cacheInvalidationCoordinator",
                mock(CacheInvalidationCoordinator.class));
    }

    @Test
    void startOrStopUpdatesItemStatus() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder().id(17L).build());

        itemService.startOrStop(StatusConstant.DISABLE, 17L);

        ArgumentCaptor<ExploreItem> itemCaptor = ArgumentCaptor.forClass(ExploreItem.class);
        verify(itemMapper).update(itemCaptor.capture());
        ExploreItem item = itemCaptor.getValue();
        assertThat(item.getId()).isEqualTo(17L);
        assertThat(item.getStatus()).isEqualTo(StatusConstant.DISABLE);
        assertThat(item.getName()).isNull();
    }

    @Test
    void startOrStopRejectsMissingItem() {
        assertThatThrownBy(() -> itemService.startOrStop(StatusConstant.DISABLE, 404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("特色项目不存在");

        verify(itemMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopRejectsInvalidStatus() {
        assertThatThrownBy(() -> itemService.startOrStop(2, 17L))
                .isInstanceOf(BaseException.class)
                .hasMessage("状态参数只能为0或1");

        verify(itemMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveInitializesBookedInsteadOfTrustingClientValue() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setName("城市咖啡体验");
        dto.setCategoryId(11L);
        dto.setBooked(999);
        stubItemCategory();

        itemService.saveWithTags(dto);

        ArgumentCaptor<ExploreItem> itemCaptor = ArgumentCaptor.forClass(ExploreItem.class);
        verify(itemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getBooked()).isZero();
    }

    @Test
    void updateDoesNotOverwriteDerivedBookedCount() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setId(1001L);
        dto.setName("城市咖啡体验");
        dto.setCategoryId(11L);
        dto.setBooked(999);
        when(itemMapper.getById(1001L)).thenReturn(ExploreItem.builder().id(1001L).build());
        stubItemCategory();

        itemService.updateWithTags(dto);

        ArgumentCaptor<ExploreItem> itemCaptor = ArgumentCaptor.forClass(ExploreItem.class);
        verify(itemMapper).update(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getBooked()).isNull();
    }

    @Test
    void saveRejectsMissingItemCategoryBeforeInsert() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setCategoryId(404L);

        assertThatThrownBy(() -> itemService.saveWithTags(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("项目分类不存在或类型不正确");

        verify(itemMapper, never()).insert(any());
        verify(itemTagMapper, never()).insertBatch(any());
    }

    @Test
    void saveRejectsPackageCategoryForItemBeforeInsert() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setCategoryId(22L);
        when(categoryMapper.getById(22L)).thenReturn(Category.builder().id(22L).type(2).build());

        assertThatThrownBy(() -> itemService.saveWithTags(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("项目分类不存在或类型不正确");

        verify(itemMapper, never()).insert(any());
        verify(itemTagMapper, never()).insertBatch(any());
    }

    @Test
    void getByIdWithTagsRejectsMissingItem() {
        assertThatThrownBy(() -> itemService.getByIdWithTags(404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("特色项目不存在");

        verify(itemTagMapper, never()).getByExploreItemId(404L);
    }

    @Test
    void updateRejectsMissingItemBeforeChangingTags() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setId(404L);
        dto.setName("不存在的项目");

        assertThatThrownBy(() -> itemService.updateWithTags(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("特色项目不存在");

        verify(itemMapper, never()).update(org.mockito.ArgumentMatchers.any());
        verify(itemTagMapper, never()).deleteByExploreItemId(404L);
    }

    @Test
    void updateRejectsMissingItemCategoryBeforeChangingTags() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setId(1001L);
        dto.setCategoryId(404L);
        when(itemMapper.getById(1001L)).thenReturn(ExploreItem.builder().id(1001L).build());

        assertThatThrownBy(() -> itemService.updateWithTags(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("项目分类不存在或类型不正确");

        verify(itemMapper, never()).update(any());
        verify(itemTagMapper, never()).deleteByExploreItemId(1001L);
    }

    @Test
    void deleteBatchRemovesTagRelationsBeforeItems() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder()
                .id(17L)
                .status(StatusConstant.DISABLE)
                .build());
        when(packageItemMapper.getPackageIdsByExploreItemIds(Collections.singletonList(17L))).thenReturn(Collections.emptyList());

        itemService.deleteBatch(Collections.singletonList(17L));

        InOrder inOrder = inOrder(itemTagMapper, itemMapper);
        inOrder.verify(itemTagMapper).deleteByExploreItemIds(Collections.singletonList(17L));
        inOrder.verify(itemMapper).deleteByIds(Collections.singletonList(17L));
    }

    @Test
    void deleteBatchRejectsEnabledItem() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder()
                .id(17L)
                .status(StatusConstant.ENABLE)
                .build());

        assertThatThrownBy(() -> itemService.deleteBatch(Collections.singletonList(17L)))
                .isInstanceOf(DeletionNotAllowedException.class);

        verify(packageItemMapper, never()).getPackageIdsByExploreItemIds(Collections.singletonList(17L));
        verify(itemTagMapper, never()).deleteByExploreItemIds(Collections.singletonList(17L));
        verify(itemMapper, never()).deleteByIds(Collections.singletonList(17L));
    }

    @Test
    void deleteBatchRejectsItemRelatedByPackage() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder()
                .id(17L)
                .status(StatusConstant.DISABLE)
                .build());
        when(packageItemMapper.getPackageIdsByExploreItemIds(Collections.singletonList(17L)))
                .thenReturn(Collections.singletonList(27L));

        assertThatThrownBy(() -> itemService.deleteBatch(Collections.singletonList(17L)))
                .isInstanceOf(DeletionNotAllowedException.class);

        verify(itemTagMapper, never()).deleteByExploreItemIds(Collections.singletonList(17L));
        verify(itemMapper, never()).deleteByIds(Collections.singletonList(17L));
    }

    @Test
    void deleteBatchRejectsItemReferencedByOrder() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder()
                .id(17L)
                .status(StatusConstant.DISABLE)
                .build());
        when(packageItemMapper.getPackageIdsByExploreItemIds(Collections.singletonList(17L)))
                .thenReturn(Collections.emptyList());
        when(orderMapper.countByItemIds(Collections.singletonList(17L))).thenReturn(1L);

        assertThatThrownBy(() -> itemService.deleteBatch(Collections.singletonList(17L)))
                .isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage("当前特色项目已有预约记录，不能删除，可改为停用");

        verify(reviewMapper, never()).countByItemId(17L);
        verify(itemMapper, never()).deleteByIds(Collections.singletonList(17L));
    }

    @Test
    void deleteBatchRejectsItemReferencedByReview() {
        when(itemMapper.getById(17L)).thenReturn(ExploreItem.builder()
                .id(17L)
                .status(StatusConstant.DISABLE)
                .build());
        when(packageItemMapper.getPackageIdsByExploreItemIds(Collections.singletonList(17L)))
                .thenReturn(Collections.emptyList());
        when(reviewMapper.countByItemId(17L)).thenReturn(1L);

        assertThatThrownBy(() -> itemService.deleteBatch(Collections.singletonList(17L)))
                .isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage("当前特色项目已有评价记录，不能删除，可改为停用");

        verify(itemMapper, never()).deleteByIds(Collections.singletonList(17L));
    }

    @Test
    void deleteBatchRejectsMissingItem() {
        when(itemMapper.getById(17L)).thenReturn(null);

        assertThatThrownBy(() -> itemService.deleteBatch(Collections.singletonList(17L)))
                .isInstanceOf(BaseException.class)
                .hasMessage("特色项目不存在");

        verify(packageItemMapper, never()).getPackageIdsByExploreItemIds(Collections.singletonList(17L));
        verify(itemMapper, never()).deleteByIds(Collections.singletonList(17L));
    }

    private void stubItemCategory() {
        when(categoryMapper.getById(11L)).thenReturn(Category.builder().id(11L).type(1).build());
    }
}
