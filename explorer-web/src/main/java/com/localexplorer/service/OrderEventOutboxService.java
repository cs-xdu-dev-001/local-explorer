package com.localexplorer.service;

import com.localexplorer.dto.OutboxPageQueryDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.result.PageResult;
import com.localexplorer.vo.OutboxStatsVO;

import java.time.LocalDateTime;

public interface OrderEventOutboxService {

    void append(ExploreOrder order, String eventType, LocalDateTime now);

    PageResult pageQuery(OutboxPageQueryDTO dto);

    OutboxStatsVO stats();

    void retryDead(Long id);
}
