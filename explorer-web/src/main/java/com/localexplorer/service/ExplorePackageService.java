package com.localexplorer.service;

import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.dto.ExplorePackagePageQueryDTO;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.result.PageResult;
import com.localexplorer.vo.PackageItemVO;
import com.localexplorer.vo.ExplorePackageVO;

import java.util.List;

public interface ExplorePackageService {

    /**
     * 新增套餐，同时需要保存套餐和特色项目的关联关系
     * @param packageDTO
     */
    void saveWithItems(ExplorePackageDTO packageDTO);
    /**
     * 条件查询
     * @param packageEntity
     * @return
     */
    List<ExplorePackage> list(ExplorePackage packageEntity);

    /**
     * 根据id查询特色项目选项
     * @param id
     * @return
     */
    List<PackageItemVO> getPackageItemsById(Long id);
    /**
     * 分页查询
     * @param packagePageQueryDTO
     * @return
     */
    PageResult pageQuery(ExplorePackagePageQueryDTO packagePageQueryDTO);
    /**
     * 批量删除套餐
     * @param ids
     */
    void deleteBatch(List<Long> ids);
    /**
     * 根据id查询套餐和关联的特色项目数据
     * @param id
     * @return
     */
    ExplorePackageVO getByIdWithItems(Long id);

    /**
     * 修改套餐
     * @param packageDTO
     */
    void update(ExplorePackageDTO packageDTO);

    /**
     * 套餐起售、停售
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);
}
