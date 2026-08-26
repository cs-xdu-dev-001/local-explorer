package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.annotation.AutoFill;
import com.localexplorer.dto.ExplorePackagePageQueryDTO;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.enumeration.OperationType;
import com.localexplorer.vo.PackageItemVO;
import com.localexplorer.vo.ExplorePackageVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ExplorePackageMapper {

    @Select("select count(id) from explore_package where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    @AutoFill(OperationType.INSERT)
    void insert(ExplorePackage packageEntity);

    List<ExplorePackage> list(ExplorePackage packageEntity);

    @Select("select pi.item_id as itemId, pi.name, pi.copies, i.image, i.description " +
            "from explore_package_item pi left join explore_item i on pi.item_id = i.id " +
            "where pi.package_id = #{packageId}")
    List<PackageItemVO> getPackageItemsByPackageId(Long packageId);

    Page<ExplorePackageVO> pageQuery(ExplorePackagePageQueryDTO packagePageQueryDTO);

    @Select("select * from explore_package where id = #{id}")
    ExplorePackage getById(Long id);

    @Update("update explore_package " +
            "set booked = coalesce(booked, 0) + #{peopleCount}, update_time = now() " +
            "where id = #{id} and status = 1 and #{peopleCount} > 0 " +
            "and (capacity is null or coalesce(booked, 0) + #{peopleCount} <= capacity)")
    int reserveCapacity(@Param("id") Long id, @Param("peopleCount") Integer peopleCount);

    @Update("update explore_package " +
            "set booked = greatest(0, coalesce(booked, 0) - #{peopleCount}), update_time = now() " +
            "where id = #{id} and #{peopleCount} > 0")
    int releaseCapacity(@Param("id") Long id, @Param("peopleCount") Integer peopleCount);

    @AutoFill(OperationType.UPDATE)
    void update(ExplorePackage packageEntity);

    @Delete("delete from explore_package where id = #{id}")
    void deleteById(Long id);
}
