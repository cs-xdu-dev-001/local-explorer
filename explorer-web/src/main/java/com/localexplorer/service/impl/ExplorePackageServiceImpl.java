package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.dto.ExplorePackagePageQueryDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.entity.ExplorePackageItem;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.exception.ExplorePackageEnableFailedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageItemMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.ExplorePackageService;
import com.localexplorer.vo.PackageItemVO;
import com.localexplorer.vo.ExplorePackageVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 探店套餐业务层
 */
@Service
@Slf4j
public class ExplorePackageServiceImpl implements ExplorePackageService {

    private static final Integer PACKAGE_CATEGORY_TYPE = 2;

    @Autowired
    private ExplorePackageMapper packageMapper;
    @Autowired
    private ExplorePackageItemMapper packageItemMapper;
    @Autowired
    private ExploreItemMapper itemMapper;
    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private HotReadCacheService hotCache;
    @Autowired
    private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 新增套餐（同时保存套餐和特色项目的关联关系）
     */
    @Transactional
    public void saveWithItems(ExplorePackageDTO packageDTO) {
        List<ExplorePackageItem> packageItems = validateAndNormalizePackageItems(
                packageDTO.getPackageItems(), packageDTO.getStatus());
        validatePackageCategory(packageDTO.getCategoryId());

        ExplorePackage packageEntity = new ExplorePackage();
        BeanUtils.copyProperties(packageDTO, packageEntity);
        packageEntity.setBooked(0);

        packageMapper.insert(packageEntity);

        Long packageId = packageEntity.getId();
        packageItems.forEach(packageItem -> packageItem.setPackageId(packageId));
        packageItemMapper.insertBatch(packageItems);
        invalidatePackageChanges(Collections.singletonList(packageId));
    }

    /**
     * 条件查询
     */
    public List<ExplorePackage> list(ExplorePackage packageEntity) {
        String key = String.valueOf(packageEntity.getCategoryId()) + ":" + String.valueOf(packageEntity.getStatus());
        return hotCache.get(HotCacheDomain.PACKAGE_LIST, key, () -> packageMapper.list(packageEntity));
    }

    /**
     * 根据id查询套餐中的特色项目选项
     */
    public List<PackageItemVO> getPackageItemsById(Long id) {
        return hotCache.get(HotCacheDomain.PACKAGE_ITEMS, String.valueOf(id),
                () -> packageMapper.getPackageItemsByPackageId(id));
    }

    /**
     * 分页查询
     */
    public PageResult pageQuery(ExplorePackagePageQueryDTO packagePageQueryDTO) {
        int pageNum = packagePageQueryDTO.getPage();
        int pageSize = packagePageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<ExplorePackageVO> page = packageMapper.pageQuery(packagePageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐（校验是否在售）
     */
    @Transactional
    public void deleteBatch(List<Long> ids) {
        ids.forEach(id -> {
            ExplorePackage packageEntity = packageMapper.getById(id);
            if (packageEntity == null) {
                throw new BaseException(MessageConstant.EXPLORE_PACKAGE_NOT_FOUND);
            }
            if (StatusConstant.ENABLE == packageEntity.getStatus()) {
                throw new DeletionNotAllowedException(MessageConstant.EXPLORE_PACKAGE_ON_SALE);
            }
        });

        if (orderMapper.countByPackageIds(ids) > 0) {
            throw new DeletionNotAllowedException(MessageConstant.PACKAGE_BE_RELATED_BY_ORDER);
        }

        ids.forEach(packageId -> {
            packageItemMapper.deleteByExplorePackageId(packageId);
            packageMapper.deleteById(packageId);
        });
        invalidatePackageChanges(ids);
    }

    /**
     * 根据id查询套餐和套餐项目关系
     */
    public ExplorePackageVO getByIdWithItems(Long id) {
        return hotCache.get(HotCacheDomain.PACKAGE_DETAIL, String.valueOf(id), () -> {
            ExplorePackage packageEntity = packageMapper.getById(id);
            if (packageEntity == null) {
                throw new BaseException(MessageConstant.EXPLORE_PACKAGE_NOT_FOUND);
            }
            List<ExplorePackageItem> packageItems = packageItemMapper.getByExplorePackageId(id);
            ExplorePackageVO packageVO = new ExplorePackageVO();
            BeanUtils.copyProperties(packageEntity, packageVO);
            packageVO.setPackageItems(packageItems);
            return packageVO;
        });
    }

    /**
     * 修改套餐（先删后插关联关系）
     */
    @Transactional
    public void update(ExplorePackageDTO packageDTO) {
        List<ExplorePackageItem> packageItems = requirePackageItems(packageDTO.getPackageItems());
        if (packageMapper.getById(packageDTO.getId()) == null) {
            throw new BaseException(MessageConstant.EXPLORE_PACKAGE_NOT_FOUND);
        }
        validateAndNormalizePackageItems(packageItems, packageDTO.getStatus());
        validatePackageCategory(packageDTO.getCategoryId());

        ExplorePackage packageEntity = new ExplorePackage();
        BeanUtils.copyProperties(packageDTO, packageEntity);
        packageEntity.setBooked(null);

        packageMapper.update(packageEntity);

        Long packageId = packageDTO.getId();
        packageItemMapper.deleteByExplorePackageId(packageId);

        packageItems.forEach(packageItem -> packageItem.setPackageId(packageId));
        packageItemMapper.insertBatch(packageItems);
        invalidatePackageChanges(Collections.singletonList(packageId));
    }

    /**
     * 套餐起售/停售
     * <p>起售时校验：套餐内不能包含停售的特色项目</p>
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        if (packageMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.EXPLORE_PACKAGE_NOT_FOUND);
        }
        if (status == StatusConstant.ENABLE) {
            List<ExploreItem> itemList = itemMapper.getByExplorePackageId(id);
            if (itemList == null || itemList.isEmpty()) {
                throw new BaseException(MessageConstant.PACKAGE_ITEMS_REQUIRED);
            }
            itemList.forEach(item -> {
                if (StatusConstant.DISABLE == item.getStatus()) {
                    throw new ExplorePackageEnableFailedException(MessageConstant.PACKAGE_ENABLE_FAILED);
                }
            });
        }

        ExplorePackage packageEntity = ExplorePackage.builder()
                .id(id)
                .status(status)
                .build();
        packageMapper.update(packageEntity);
        invalidatePackageChanges(Collections.singletonList(id));
    }

    private void invalidatePackageChanges(List<Long> packageIds) {
        CacheInvalidation.Builder invalidation = CacheInvalidation.builder()
                .clear(HotCacheDomain.PACKAGE_LIST);
        if (packageIds != null) {
            packageIds.forEach(id -> invalidation
                    .evict(HotCacheDomain.PACKAGE_DETAIL, id)
                    .evict(HotCacheDomain.PACKAGE_ITEMS, id));
        }
        cacheInvalidationCoordinator.invalidate(invalidation.build());
    }

    private List<ExplorePackageItem> requirePackageItems(List<ExplorePackageItem> packageItems) {
        if (packageItems == null || packageItems.isEmpty()) {
            throw new BaseException(MessageConstant.PACKAGE_ITEMS_REQUIRED);
        }
        return packageItems;
    }

    private List<ExplorePackageItem> validateAndNormalizePackageItems(
            List<ExplorePackageItem> packageItems, Integer packageStatus) {
        List<ExplorePackageItem> requiredItems = requirePackageItems(packageItems);
        Map<Long, ExplorePackageItem> relationsByItemId = new LinkedHashMap<>();
        for (ExplorePackageItem relation : requiredItems) {
            if (relation == null || relation.getItemId() == null) {
                throw new BaseException(MessageConstant.PACKAGE_ITEM_NOT_FOUND);
            }
            if (relation.getCopies() == null || relation.getCopies() < 1 || relation.getCopies() > 99) {
                throw new BaseException(MessageConstant.PARAM_ERROR);
            }
            if (relationsByItemId.put(relation.getItemId(), relation) != null) {
                throw new BaseException(MessageConstant.PACKAGE_ITEM_DUPLICATED);
            }
        }

        List<Long> itemIds = new ArrayList<>(relationsByItemId.keySet());
        List<ExploreItem> databaseItems = itemMapper.listByIds(itemIds);
        if (databaseItems == null || databaseItems.size() != itemIds.size()) {
            throw new BaseException(MessageConstant.PACKAGE_ITEM_NOT_FOUND);
        }

        Map<Long, ExploreItem> itemsById = new LinkedHashMap<>();
        for (ExploreItem item : databaseItems) {
            if (item != null && item.getId() != null) {
                itemsById.put(item.getId(), item);
            }
        }
        if (itemsById.size() != itemIds.size()) {
            throw new BaseException(MessageConstant.PACKAGE_ITEM_NOT_FOUND);
        }

        for (Map.Entry<Long, ExplorePackageItem> entry : relationsByItemId.entrySet()) {
            ExploreItem item = itemsById.get(entry.getKey());
            if (item == null) {
                throw new BaseException(MessageConstant.PACKAGE_ITEM_NOT_FOUND);
            }
            if (StatusConstant.ENABLE.equals(packageStatus)
                    && !StatusConstant.ENABLE.equals(item.getStatus())) {
                throw new ExplorePackageEnableFailedException(MessageConstant.PACKAGE_ENABLE_FAILED);
            }
            ExplorePackageItem relation = entry.getValue();
            relation.setName(item.getName());
            relation.setPrice(item.getPrice());
        }
        return requiredItems;
    }

    private void validatePackageCategory(Long categoryId) {
        Category category = categoryId == null ? null : categoryMapper.getById(categoryId);
        if (category == null || !PACKAGE_CATEGORY_TYPE.equals(category.getType())) {
            throw new BaseException(MessageConstant.PACKAGE_CATEGORY_INVALID);
        }
    }
}
