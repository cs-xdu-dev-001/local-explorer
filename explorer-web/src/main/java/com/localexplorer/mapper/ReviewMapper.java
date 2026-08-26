package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.Review;
import com.localexplorer.vo.ReviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReviewMapper {

    void insert(Review review);

    Page<ReviewVO> pageQuery(ReviewPageQueryDTO dto);

    @Select("select * from review where id = #{id}")
    Review getById(Long id);

    void updateReply(Review review);

    void deleteByIds(List<Long> ids);

    @Select("select count(*) from review where item_id = #{itemId}")
    Long countByItemId(Long itemId);

    @Select("select count(*) from review where order_id = #{orderId}")
    Long countByOrderId(Long orderId);

    @Select("select avg(rating) from review where item_id = #{itemId}")
    Double avgRatingByItemId(Long itemId);

    /** Dashboard: 按日期统计评价数 */
    List<Map<String, Object>> countByDate(@org.apache.ibatis.annotations.Param("days") int days);

    Long findMaxIdForExport(ExportQuerySnapshot snapshot);

    long countForExport(ExportQuerySnapshot snapshot);

    List<ReviewVO> findExportChunk(@Param("snapshot") ExportQuerySnapshot snapshot,
                                   @Param("lastId") long lastId,
                                   @Param("limit") int limit);
}
