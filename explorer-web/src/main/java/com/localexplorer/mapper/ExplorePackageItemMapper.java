package com.localexplorer.mapper;

import com.localexplorer.entity.ExplorePackageItem;
import com.localexplorer.vo.PackageItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ExplorePackageItemMapper {

    List<Long> getPackageIdsByExploreItemIds(@Param("itemIds") List<Long> itemIds);

    void insertBatch(@Param("packageItems") List<ExplorePackageItem> packageItems);

    @Select("select pi.item_id as itemId, pi.name, pi.copies, i.image, i.description " +
            "from explore_package_item pi left join explore_item i on pi.item_id = i.id " +
            "where pi.package_id = #{packageId}")
    List<PackageItemVO> getPackageItemsByPackageId(Long packageId);

    /**
     * 根据套餐id删除套餐和特色项目的关联关系
     * @param packageId
     */
    @Delete("delete from explore_package_item where package_id = #{packageId}")
    void deleteByExplorePackageId(Long packageId);

    /**
     * 根据套餐id查询套餐和特色项目的关联关系
     * @param packageId
     * @return
     */
    @Select("select id, package_id, item_id, name, price, copies from explore_package_item where package_id = #{packageId}")
    List<ExplorePackageItem> getByExplorePackageId(Long packageId);
}
