package com.localexplorer.service.impl;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheTestDoubles;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.entity.ExplorePackageItem;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ExplorePackageServiceImplTest {

    private ExplorePackageServiceImpl packageService;

    @Mock
    private ExplorePackageMapper packageMapper;
    @Mock
    private ExplorePackageItemMapper packageItemMapper;
    @Mock
    private ExploreItemMapper itemMapper;
    @Mock
    private ExploreOrderMapper orderMapper;
    @Mock
    private CategoryMapper categoryMapper;

    @BeforeEach
    void setUp() {
        packageService = new ExplorePackageServiceImpl();
        ReflectionTestUtils.setField(packageService, "packageMapper", packageMapper);
        ReflectionTestUtils.setField(packageService, "packageItemMapper", packageItemMapper);
        ReflectionTestUtils.setField(packageService, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(packageService, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(packageService, "categoryMapper", categoryMapper);
        ReflectionTestUtils.setField(packageService, "hotCache", HotCacheTestDoubles.passThroughCache());
        ReflectionTestUtils.setField(packageService, "cacheInvalidationCoordinator",
                mock(CacheInvalidationCoordinator.class));
    }

    @Test
    void savePackageRejectsEmptyItemRelation() {
        ExplorePackageDTO dto = new ExplorePackageDTO();
        dto.setName("空套餐");

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(MessageConstant.PACKAGE_ITEMS_REQUIRED);

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void updatePackageRejectsEmptyItemRelationBeforeDeletingExistingItems() {
        ExplorePackageDTO dto = new ExplorePackageDTO();
        dto.setId(2001L);
        dto.setName("空套餐");

        assertThatThrownBy(() -> packageService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(MessageConstant.PACKAGE_ITEMS_REQUIRED);

        verify(packageMapper, never()).update(any());
        verify(packageItemMapper, never()).deleteByExplorePackageId(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void enablingPackageRejectsPackageWithoutItems() {
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder().id(2001L).build());
        when(itemMapper.getByExplorePackageId(2001L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> packageService.startOrStop(StatusConstant.ENABLE, 2001L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(MessageConstant.PACKAGE_ITEMS_REQUIRED);

        verify(packageMapper, never()).update(any());
    }

    @Test
    void enablingPackageRejectsDisabledItem() {
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder().id(2001L).build());
        ExploreItem disabledItem = ExploreItem.builder()
                .id(1001L)
                .name("停用项目")
                .status(StatusConstant.DISABLE)
                .build();
        when(itemMapper.getByExplorePackageId(2001L)).thenReturn(Collections.singletonList(disabledItem));

        assertThatThrownBy(() -> packageService.startOrStop(StatusConstant.ENABLE, 2001L))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(MessageConstant.PACKAGE_ENABLE_FAILED);

        verify(packageMapper, never()).update(any());
    }

    @Test
    void startOrStopRejectsMissingPackage() {
        assertThatThrownBy(() -> packageService.startOrStop(StatusConstant.DISABLE, 404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("探店套餐不存在");

        verify(packageMapper, never()).update(any());
    }

    @Test
    void startOrStopRejectsInvalidStatus() {
        assertThatThrownBy(() -> packageService.startOrStop(2, 2001L))
                .isInstanceOf(BaseException.class)
                .hasMessage("状态参数只能为0或1");

        verify(packageMapper, never()).update(any());
    }

    @Test
    void savePackageAcceptsSelectedItems() {
        ExplorePackageDTO dto = new ExplorePackageDTO();
        dto.setName("城市周末套餐");
        dto.setCategoryId(21L);
        dto.setPackageItems(Collections.singletonList(
                ExplorePackageItem.builder()
                        .itemId(1001L)
                        .name("咖啡体验")
                        .price(new BigDecimal("39.00"))
                        .copies(1)
                        .build()
                ));
        stubExistingItem();
        stubPackageCategory();

        packageService.saveWithItems(dto);

        verify(packageMapper).insert(any(ExplorePackage.class));
        verify(packageItemMapper).insertBatch(dto.getPackageItems());
    }

    @Test
    void savePackageRejectsUnknownItemBeforeInsert() {
        ExplorePackageDTO dto = packageDtoWithItem();
        when(itemMapper.listByIds(Collections.singletonList(1001L))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("套餐包含的特色项目不存在或已删除");

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void savePackageRejectsDuplicateItemIds() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setPackageItems(Arrays.asList(
                ExplorePackageItem.builder().itemId(1001L).copies(1).build(),
                ExplorePackageItem.builder().itemId(1001L).copies(2).build()
        ));

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("套餐不能重复包含同一特色项目");

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void savePackageRejectsMissingPackageCategoryBeforeInsert() {
        ExplorePackageDTO dto = packageDtoWithItem();
        stubExistingItem();

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("套餐分类不存在或类型不正确");

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void savePackageRejectsItemCategoryForPackageBeforeInsert() {
        ExplorePackageDTO dto = packageDtoWithItem();
        stubExistingItem();
        when(categoryMapper.getById(21L)).thenReturn(Category.builder().id(21L).type(1).build());

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("套餐分类不存在或类型不正确");

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void savePackageUsesDatabaseItemNameAndPrice() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.getPackageItems().get(0).setName("伪造名称");
        dto.getPackageItems().get(0).setPrice(new BigDecimal("0.01"));
        when(itemMapper.listByIds(Collections.singletonList(1001L))).thenReturn(Collections.singletonList(
                ExploreItem.builder()
                        .id(1001L)
                        .name("咖啡体验")
                        .price(new BigDecimal("39.00"))
                        .status(StatusConstant.ENABLE)
                        .build()
        ));
        stubPackageCategory();

        packageService.saveWithItems(dto);

        verify(packageItemMapper).insertBatch(dto.getPackageItems());
        assertThat(dto.getPackageItems().get(0).getName()).isEqualTo("咖啡体验");
        assertThat(dto.getPackageItems().get(0).getPrice()).isEqualByComparingTo("39.00");
    }

    @Test
    void saveEnabledPackageRejectsDisabledItem() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setStatus(StatusConstant.ENABLE);
        when(itemMapper.listByIds(Collections.singletonList(1001L))).thenReturn(Collections.singletonList(
                ExploreItem.builder()
                        .id(1001L)
                        .name("已停用项目")
                        .status(StatusConstant.DISABLE)
                        .build()
        ));

        assertThatThrownBy(() -> packageService.saveWithItems(dto))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining(MessageConstant.PACKAGE_ENABLE_FAILED);

        verify(packageMapper, never()).insert(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void updatePackageRejectsUnknownItemBeforeChangingExistingRelations() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setId(2001L);
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder().id(2001L).build());
        when(itemMapper.listByIds(Collections.singletonList(1001L))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> packageService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("套餐包含的特色项目不存在或已删除");

        verify(packageMapper, never()).update(any());
        verify(packageItemMapper, never()).deleteByExplorePackageId(any());
        verify(packageItemMapper, never()).insertBatch(any());
    }

    @Test
    void saveInitializesBookedInsteadOfTrustingClientValue() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setBooked(999);
        stubExistingItem();
        stubPackageCategory();

        packageService.saveWithItems(dto);

        ArgumentCaptor<ExplorePackage> packageCaptor = ArgumentCaptor.forClass(ExplorePackage.class);
        verify(packageMapper).insert(packageCaptor.capture());
        assertThat(packageCaptor.getValue().getBooked()).isZero();
    }

    @Test
    void updateDoesNotOverwriteDerivedBookedCount() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setId(2001L);
        dto.setBooked(999);
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder().id(2001L).build());
        stubExistingItem();
        stubPackageCategory();

        packageService.update(dto);

        ArgumentCaptor<ExplorePackage> packageCaptor = ArgumentCaptor.forClass(ExplorePackage.class);
        verify(packageMapper).update(packageCaptor.capture());
        assertThat(packageCaptor.getValue().getBooked()).isNull();
    }

    @Test
    void getByIdWithItemsRejectsMissingPackage() {
        assertThatThrownBy(() -> packageService.getByIdWithItems(404L))
                .isInstanceOf(BaseException.class)
                .hasMessage("探店套餐不存在");

        verify(packageItemMapper, never()).getByExplorePackageId(404L);
    }

    @Test
    void updateRejectsMissingPackageBeforeChangingItems() {
        ExplorePackageDTO dto = packageDtoWithItem();
        dto.setId(404L);

        assertThatThrownBy(() -> packageService.update(dto))
                .isInstanceOf(BaseException.class)
                .hasMessage("探店套餐不存在");

        verify(packageMapper, never()).update(any());
        verify(packageItemMapper, never()).deleteByExplorePackageId(404L);
    }

    @Test
    void deleteBatchRemovesItemRelationsBeforePackage() {
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder()
                .id(2001L)
                .status(StatusConstant.DISABLE)
                .build());

        packageService.deleteBatch(Collections.singletonList(2001L));

        InOrder inOrder = inOrder(packageItemMapper, packageMapper);
        inOrder.verify(packageItemMapper).deleteByExplorePackageId(2001L);
        inOrder.verify(packageMapper).deleteById(2001L);
    }

    @Test
    void deleteBatchRejectsEnabledPackage() {
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder()
                .id(2001L)
                .status(StatusConstant.ENABLE)
                .build());

        assertThatThrownBy(() -> packageService.deleteBatch(Collections.singletonList(2001L)))
                .isInstanceOf(DeletionNotAllowedException.class);

        verify(packageItemMapper, never()).deleteByExplorePackageId(2001L);
        verify(packageMapper, never()).deleteById(2001L);
    }

    @Test
    void deleteBatchRejectsPackageReferencedByOrder() {
        when(packageMapper.getById(2001L)).thenReturn(ExplorePackage.builder()
                .id(2001L)
                .status(StatusConstant.DISABLE)
                .build());
        when(orderMapper.countByPackageIds(Collections.singletonList(2001L))).thenReturn(1L);

        assertThatThrownBy(() -> packageService.deleteBatch(Collections.singletonList(2001L)))
                .isInstanceOf(DeletionNotAllowedException.class)
                .hasMessage("当前探店套餐已有预约记录，不能删除，可改为停用");

        verify(packageItemMapper, never()).deleteByExplorePackageId(2001L);
        verify(packageMapper, never()).deleteById(2001L);
    }

    @Test
    void deleteBatchRejectsMissingPackage() {
        when(packageMapper.getById(2001L)).thenReturn(null);

        assertThatThrownBy(() -> packageService.deleteBatch(Collections.singletonList(2001L)))
                .isInstanceOf(BaseException.class)
                .hasMessage("探店套餐不存在");

        verify(orderMapper, never()).countByPackageIds(Collections.singletonList(2001L));
        verify(packageMapper, never()).deleteById(2001L);
    }

    private ExplorePackageDTO packageDtoWithItem() {
        ExplorePackageDTO dto = new ExplorePackageDTO();
        dto.setName("城市周末套餐");
        dto.setCategoryId(21L);
        dto.setPackageItems(Collections.singletonList(
                ExplorePackageItem.builder()
                        .itemId(1001L)
                        .name("咖啡体验")
                        .price(new BigDecimal("39.00"))
                        .copies(1)
                        .build()
        ));
        return dto;
    }

    private void stubExistingItem() {
        when(itemMapper.listByIds(Collections.singletonList(1001L))).thenReturn(Collections.singletonList(
                ExploreItem.builder()
                        .id(1001L)
                        .name("咖啡体验")
                        .price(new BigDecimal("39.00"))
                        .status(StatusConstant.ENABLE)
                        .build()
        ));
    }

    private void stubPackageCategory() {
        when(categoryMapper.getById(21L)).thenReturn(Category.builder().id(21L).type(2).build());
    }
}
