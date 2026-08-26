package com.localexplorer.mapper;

import com.localexplorer.entity.ExploreItemTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExploreItemTagMapper {
    /**
     * 批量插入
     * @param tags
     */
    void insertBatch(@Param("tags") List<ExploreItemTag> tags);

    /**
     * 根据特色项目id删除标签数据
     * @param itemId
     */
    @Delete("delete from explore_item_tag where item_id = #{itemId}")
    void deleteByExploreItemId(Long itemId);

    /**
     * 根据特色项目id集合批量删除关联的标签数据
     * @param itemIds
     */
    void deleteByExploreItemIds(@Param("itemIds") List<Long> itemIds);

    /**
     * 根据特色项目id查询对应标签数据
     * @param itemId
     * @return
     */
    @Select("select * from explore_item_tag where item_id = #{itemId}")
    List<ExploreItemTag> getByExploreItemId(Long itemId);
}
