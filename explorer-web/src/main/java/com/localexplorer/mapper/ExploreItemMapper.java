package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.annotation.AutoFill;
import com.localexplorer.dto.ExploreItemPageQueryDTO;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.enumeration.OperationType;
import com.localexplorer.vo.ExploreItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExploreItemMapper {

    /**
     * 根据分类id查询特色项目数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from explore_item where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入特色项目
     * @param item
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(ExploreItem item);

    /**
     * 特色项目分页查询
     * @return
     */
    Page<ExploreItemVO> pageQuery(ExploreItemPageQueryDTO itemPageQueryDTO);

    /**
     * 根据id查特色项目
     * @param id
     * @return
     */
    @Select("select * from explore_item where id = #{id}")
    ExploreItem getById(Long id);

    @Update("update explore_item " +
            "set booked = coalesce(booked, 0) + #{peopleCount}, update_time = now() " +
            "where id = #{id} and status = 1 and #{peopleCount} > 0 " +
            "and (capacity is null or coalesce(booked, 0) + #{peopleCount} <= capacity)")
    int reserveCapacity(@Param("id") Long id, @Param("peopleCount") Integer peopleCount);

    @Update("update explore_item " +
            "set booked = greatest(0, coalesce(booked, 0) - #{peopleCount}), update_time = now() " +
            "where id = #{id} and #{peopleCount} > 0")
    int releaseCapacity(@Param("id") Long id, @Param("peopleCount") Integer peopleCount);

    List<ExploreItem> listByIds(@Param("ids") List<Long> ids);

    /**
     * 根据主键删除特色项目
     * @param id
     */
    @Delete("delete from explore_item where id = #{id}")
    void deleteById(Long id);

    /**
     * 根据特色项目id集合批量删除特色项目
     * @param ids
     */
    void deleteByIds(List<Long> ids);

    /**
     * 修改特色项目基本信息
     * @param item
     */
    @AutoFill(value = OperationType.UPDATE)
    void update(ExploreItem item);

    /**
     * 动态条件查询特色项目
     * @param item
     * @return
     */
    List<ExploreItem> list(ExploreItem item);

    /**
     * 根据套餐id查询特色项目
     * @param packageId
     * @return
     */
    @Select("select i.* from explore_item i left join explore_package_item pi on i.id = pi.item_id where pi.package_id = #{packageId}")
    List<ExploreItem> getByExplorePackageId(Long packageId);
}
