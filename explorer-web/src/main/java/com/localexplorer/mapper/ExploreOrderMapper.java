package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.vo.ExploreOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExploreOrderMapper {

    void insert(ExploreOrder order);

    Page<ExploreOrderVO> pageQuery(ExploreOrderPageQueryDTO dto);

    @Select("select * from explore_order where id = #{id}")
    ExploreOrder getById(Long id);

    @Select("select * from explore_order where user_id = #{userId} and request_id = #{requestId}")
    ExploreOrder getByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Update("update explore_order set status = #{nextStatus}, cancel_type = #{cancelType}, " +
            "cancel_reason = #{cancelReason}, update_time = #{updateTime} " +
            "where id = #{id} and status = #{currentStatus}")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("currentStatus") Integer currentStatus,
                              @Param("nextStatus") Integer nextStatus,
                              @Param("cancelType") String cancelType,
                              @Param("cancelReason") String cancelReason,
                              @Param("updateTime") LocalDateTime updateTime);

    @Update("update explore_order set status = #{nextStatus}, cancel_type = #{cancelType}, " +
            "cancel_reason = #{cancelReason}, update_time = #{now} " +
            "where id = #{id} and status = #{currentStatus} and expire_at <= #{now}")
    int expireIfDue(@Param("id") Long id,
                    @Param("currentStatus") Integer currentStatus,
                    @Param("nextStatus") Integer nextStatus,
                    @Param("cancelType") String cancelType,
                    @Param("cancelReason") String cancelReason,
                    @Param("now") LocalDateTime now);

    @Select("select id from explore_order where status = #{status} and expire_at is not null " +
            "and expire_at <= #{now} order by expire_at, id limit #{limit}")
    List<Long> findExpiredIds(@Param("status") Integer status,
                              @Param("now") LocalDateTime now,
                              @Param("limit") int limit);

    void update(ExploreOrder order);

    @Select("select count(*) from explore_order where user_id = #{userId}")
    Long countByUserId(Long userId);

    long countByItemIds(@Param("itemIds") List<Long> itemIds);

    long countByPackageIds(@Param("packageIds") List<Long> packageIds);

    /** Dashboard: 按日期统计预约数 */
    List<Map<String, Object>> countByDate(@org.apache.ibatis.annotations.Param("days") int days);

    @Select("select count(*) from explore_order where status = #{status}")
    Long countPending(@Param("status") Integer status);

    Long findMaxIdForExport(ExportQuerySnapshot snapshot);

    long countForExport(ExportQuerySnapshot snapshot);

    List<ExploreOrderVO> findExportChunk(@Param("snapshot") ExportQuerySnapshot snapshot,
                                         @Param("lastId") long lastId,
                                         @Param("limit") int limit);

    @Select("select coalesce(sum(amount), 0) from explore_order " +
            "where status in (#{confirmedStatus}, #{completedStatus}) " +
            "and create_time >= date_sub(curdate(), interval #{days} day)")
    BigDecimal sumConfirmedRevenueSince(@Param("confirmedStatus") Integer confirmedStatus,
                                        @Param("completedStatus") Integer completedStatus,
                                        @Param("days") int days);

    @Select("select count(*) from explore_order " +
            "where status = #{status} and create_time >= date_sub(curdate(), interval #{days} day)")
    Long countByStatusSince(@Param("status") Integer status, @Param("days") int days);
}
