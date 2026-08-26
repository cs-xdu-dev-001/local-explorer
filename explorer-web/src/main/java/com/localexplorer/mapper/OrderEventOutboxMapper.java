package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.OutboxPageQueryDTO;
import com.localexplorer.entity.OrderEventOutbox;
import com.localexplorer.vo.OutboxStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderEventOutboxMapper {

    void insert(OrderEventOutbox event);

    OrderEventOutbox getById(Long id);

    List<Long> findReadyIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    int claim(@Param("id") Long id,
              @Param("lockToken") String lockToken,
              @Param("now") LocalDateTime now,
              @Param("lockedUntil") LocalDateTime lockedUntil);

    int markProcessed(@Param("id") Long id,
                      @Param("lockToken") String lockToken,
                      @Param("now") LocalDateTime now);

    int markRetry(@Param("id") Long id,
                  @Param("lockToken") String lockToken,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("lastError") String lastError,
                  @Param("now") LocalDateTime now);

    int markDead(@Param("id") Long id,
                 @Param("lockToken") String lockToken,
                 @Param("retryCount") int retryCount,
                 @Param("lastError") String lastError,
                 @Param("now") LocalDateTime now);

    int resetDead(@Param("id") Long id, @Param("now") LocalDateTime now);

    Page<OrderEventOutbox> pageQuery(OutboxPageQueryDTO dto);

    OutboxStatsVO stats();

    long countDead();
}
