package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.CategoryDTO;
import com.localexplorer.dto.CategoryPageQueryDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.mapper.CategoryMapper;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分类业务层
 */
@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ExploreItemMapper itemMapper;
    @Autowired
    private ExplorePackageMapper packageMapper;
    @Autowired
    private HotReadCacheService hotCache;
    @Autowired
    private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 新增分类
     */
    @Transactional
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setStatus(StatusConstant.DISABLE);
        categoryMapper.insert(category);
        invalidateCategoryLists();
    }

    /**
     * 分页查询
     */
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据id删除分类（校验是否被关联）
     */
    @Transactional
    public void deleteById(Long id) {
        if (categoryMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        }
        Integer count = itemMapper.countByCategoryId(id);
        if (count > 0) {
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_ITEM);
        }

        count = packageMapper.countByCategoryId(id);
        if (count > 0) {
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_PACKAGE);
        }

        categoryMapper.deleteById(id);
        invalidateCategoryLists();
    }

    /**
     * 修改分类
     */
    @Transactional
    public void update(CategoryDTO categoryDTO) {
        Category existing = categoryMapper.getById(categoryDTO.getId());
        if (existing == null) {
            throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        }
        if (categoryDTO.getType() != null
                && !categoryDTO.getType().equals(existing.getType())
                && hasRelatedContent(categoryDTO.getId())) {
            throw new BaseException(MessageConstant.CATEGORY_TYPE_CHANGE_NOT_ALLOWED);
        }
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        categoryMapper.update(category);
        invalidateCategoryLists();
    }

    /**
     * 启用、禁用分类
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        if (categoryMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        }
        Category category = Category.builder()
                .id(id)
                .status(status)
                .build();
        categoryMapper.update(category);
        invalidateCategoryLists();
    }

    /**
     * 根据类型查询分类（带缓存）
     */
    public List<Category> list(Integer type) {
        String key = type == null ? "all" : String.valueOf(type);
        return hotCache.get(HotCacheDomain.CATEGORY_LIST, key, () -> categoryMapper.list(type));
    }

    private void invalidateCategoryLists() {
        cacheInvalidationCoordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.CATEGORY_LIST)
                .clear(HotCacheDomain.ITEM_LIST)
                .clear(HotCacheDomain.PACKAGE_LIST)
                .build());
    }

    private boolean hasRelatedContent(Long categoryId) {
        Integer itemCount = itemMapper.countByCategoryId(categoryId);
        if (itemCount != null && itemCount > 0) {
            return true;
        }
        Integer packageCount = packageMapper.countByCategoryId(categoryId);
        return packageCount != null && packageCount > 0;
    }
}
