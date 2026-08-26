package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.dto.ExploreItemPageQueryDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExploreItemTag;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreItemTagMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ReviewMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.vo.ExploreItemVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 特色项目业务层
 */
@Service
@Slf4j
public class ExploreItemServiceImpl implements ExploreItemService {

    private static final Integer ITEM_CATEGORY_TYPE = 1;

    @Autowired
    private ExploreItemMapper itemMapper;
    @Autowired
    private ExploreItemTagMapper itemTagMapper;
    @Autowired
    private ExplorePackageItemMapper packageItemMapper;
    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private ReviewMapper reviewMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private HotReadCacheService hotCache;
    @Autowired
    private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 新增特色项目和对应标签
     */
    @Transactional
    public void saveWithTags(ExploreItemDTO itemDTO) {
        validateItemCategory(itemDTO.getCategoryId());

        ExploreItem item = new ExploreItem();
        BeanUtils.copyProperties(itemDTO, item);
        item.setBooked(0);
        itemMapper.insert(item);

        Long itemId = item.getId();
        List<ExploreItemTag> tags = itemDTO.getTags();
        if (tags != null && !tags.isEmpty()) {
            tags.forEach(tag -> tag.setItemId(itemId));
            itemTagMapper.insertBatch(tags);
        }
        invalidateItemChanges(Collections.singletonList(itemId), Collections.emptyList());
    }

    /**
     * 特色项目分页查询
     */
    public PageResult pageQuery(ExploreItemPageQueryDTO itemPageQueryDTO) {
        PageHelper.startPage(itemPageQueryDTO.getPage(), itemPageQueryDTO.getPageSize());
        Page<ExploreItemVO> page = itemMapper.pageQuery(itemPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 特色项目批量删除（校验是否在售、是否被套餐关联）
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            ExploreItem item = itemMapper.getById(id);
            if (item == null) {
                throw new BaseException(MessageConstant.EXPLORE_ITEM_NOT_FOUND);
            }
            if (item.getStatus() == StatusConstant.ENABLE) {
                throw new DeletionNotAllowedException(MessageConstant.EXPLORE_ITEM_ON_SALE);
            }
        }

        List<Long> packageIds = packageItemMapper.getPackageIdsByExploreItemIds(ids);
        if (packageIds != null && !packageIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.ITEM_BE_RELATED_BY_PACKAGE);
        }

        if (orderMapper.countByItemIds(ids) > 0) {
            throw new DeletionNotAllowedException(MessageConstant.ITEM_BE_RELATED_BY_ORDER);
        }

        for (Long id : ids) {
            if (reviewMapper.countByItemId(id) > 0) {
                throw new DeletionNotAllowedException(MessageConstant.ITEM_BE_RELATED_BY_REVIEW);
            }
        }

        itemTagMapper.deleteByExploreItemIds(ids);
        itemMapper.deleteByIds(ids);
        invalidateItemChanges(ids, packageIds);
    }

    /**
     * 根据id查询特色项目和对应的标签数据
     */
    public ExploreItemVO getByIdWithTags(Long id) {
        return hotCache.get(HotCacheDomain.ITEM_DETAIL, String.valueOf(id), () -> {
            ExploreItem item = itemMapper.getById(id);
            if (item == null) {
                throw new BaseException(MessageConstant.EXPLORE_ITEM_NOT_FOUND);
            }
            List<ExploreItemTag> itemTags = itemTagMapper.getByExploreItemId(id);
            ExploreItemVO itemVO = new ExploreItemVO();
            BeanUtils.copyProperties(item, itemVO);
            itemVO.setTags(itemTags);
            return itemVO;
        });
    }

    /**
     * 修改特色项目（先删后插标签）
     */
    @Transactional
    public void updateWithTags(ExploreItemDTO itemDTO) {
        if (itemMapper.getById(itemDTO.getId()) == null) {
            throw new BaseException(MessageConstant.EXPLORE_ITEM_NOT_FOUND);
        }
        validateItemCategory(itemDTO.getCategoryId());

        ExploreItem item = new ExploreItem();
        BeanUtils.copyProperties(itemDTO, item);
        item.setBooked(null);
        itemMapper.update(item);

        itemTagMapper.deleteByExploreItemId(item.getId());
        List<ExploreItemTag> tags = itemDTO.getTags();
        if (tags != null && !tags.isEmpty()) {
            tags.forEach(tag -> tag.setItemId(item.getId()));
            itemTagMapper.insertBatch(tags);
        }
        List<Long> packageIds = packageItemMapper.getPackageIdsByExploreItemIds(
                Collections.singletonList(item.getId()));
        invalidateItemChanges(Collections.singletonList(item.getId()), packageIds);
    }

    /**
     * 条件查询特色项目和标签（用户端，带缓存）
     */
    public List<ExploreItemVO> listWithTags(ExploreItem item) {
        String key = String.valueOf(item.getCategoryId()) + ":" + String.valueOf(item.getStatus());
        return hotCache.get(HotCacheDomain.ITEM_LIST, key, () -> {
            List<ExploreItem> itemList = itemMapper.list(item);
            List<ExploreItemVO> itemVOList = new ArrayList<>();
            for (ExploreItem listItem : itemList) {
                ExploreItemVO itemVO = new ExploreItemVO();
                BeanUtils.copyProperties(listItem, itemVO);
                List<ExploreItemTag> tags = itemTagMapper.getByExploreItemId(listItem.getId());
                itemVO.setTags(tags);
                itemVOList.add(itemVO);
            }
            return itemVOList;
        });
    }

    /**
     * 根据分类id查询已启用的特色项目
     */
    public List<ExploreItem> list(Long categoryId) {
        ExploreItem item = ExploreItem.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();
        return itemMapper.list(item);
    }

    @Transactional
    public void startOrStop(Integer status, Long id) {
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        if (itemMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.EXPLORE_ITEM_NOT_FOUND);
        }
        ExploreItem item = ExploreItem.builder()
                .id(id)
                .status(status)
                .build();
        itemMapper.update(item);
        List<Long> packageIds = packageItemMapper.getPackageIdsByExploreItemIds(
                Collections.singletonList(id));
        invalidateItemChanges(Collections.singletonList(id), packageIds);
    }

    private void invalidateItemChanges(List<Long> itemIds, List<Long> packageIds) {
        CacheInvalidation.Builder invalidation = CacheInvalidation.builder()
                .clear(HotCacheDomain.ITEM_LIST)
                .clear(HotCacheDomain.PACKAGE_LIST);
        if (itemIds != null) {
            itemIds.forEach(id -> invalidation.evict(HotCacheDomain.ITEM_DETAIL, id));
        }
        if (packageIds != null) {
            packageIds.forEach(id -> invalidation
                    .evict(HotCacheDomain.PACKAGE_DETAIL, id)
                    .evict(HotCacheDomain.PACKAGE_ITEMS, id));
        }
        cacheInvalidationCoordinator.invalidate(invalidation.build());
    }

    private void validateItemCategory(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.getById(categoryId);
        if (category == null || !ITEM_CATEGORY_TYPE.equals(category.getType())) {
            throw new BaseException(MessageConstant.ITEM_CATEGORY_INVALID);
        }
    }
}
