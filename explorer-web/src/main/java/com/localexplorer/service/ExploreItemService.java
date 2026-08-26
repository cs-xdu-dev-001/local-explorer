package com.localexplorer.service;

import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.dto.ExploreItemPageQueryDTO;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.result.PageResult;
import com.localexplorer.vo.ExploreItemVO;

import java.util.List;

public interface ExploreItemService {
    /**
     * 新增特色项目和对应标签
     * @param itemDTO
     */
    public void saveWithTags(ExploreItemDTO itemDTO);

    /**
     * 特色项目分页查询
     * @return
     */
    PageResult pageQuery(ExploreItemPageQueryDTO itemPageQueryDTO);

    /**
     * 特色项目批量删除
     * @param ids
     */
    void deleteBatch(List<Long> ids);
    /**
     * 根据id查询特色项目
     * @param id
     * @return
     */
    ExploreItemVO getByIdWithTags(Long id);

    /**
     * 修改特色项目
     * @param itemDTO
     * @return
     */
    void updateWithTags(ExploreItemDTO itemDTO);

    /**
     * 条件查询特色项目和标签
     * @param item
     * @return
     */
    List<ExploreItemVO> listWithTags(ExploreItem item);
    /**
     * 根据分类id查询特色项目
     * @param categoryId
     * @return
     */
    List<ExploreItem> list(Long categoryId);

    void startOrStop(Integer status, Long id);

}
