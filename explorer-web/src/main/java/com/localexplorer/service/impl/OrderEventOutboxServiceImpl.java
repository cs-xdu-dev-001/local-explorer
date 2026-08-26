package com.localexplorer.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.domain.OutboxStatus;
import com.localexplorer.dto.OutboxPageQueryDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.OrderEventOutbox;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.vo.OutboxStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderEventOutboxServiceImpl implements OrderEventOutboxService {

    @Autowired
    private OrderEventOutboxMapper outboxMapper;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrderExpirationPolicy expirationPolicy;

    @Override
    public void append(ExploreOrder order, String eventType, LocalDateTime now) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", order.getId());
        snapshot.put("orderNo", order.getOrderNo());
        snapshot.put("itemName", order.getItemName());
        snapshot.put("status", order.getStatus());
        snapshot.put("cancelType", order.getCancelType());
        snapshot.put("cancelReason", order.getCancelReason());

        OrderEventOutbox event = OrderEventOutbox.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .eventType(eventType)
                .aggregateId(order.getId())
                .userId(order.getUserId())
                .payload(writePayload(snapshot))
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .nextRetryAt(now)
                .createTime(now)
                .updateTime(now)
                .build();
        outboxMapper.insert(event);
    }

    @Override
    public PageResult pageQuery(OutboxPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<OrderEventOutbox> page = outboxMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public OutboxStatsVO stats() {
        OutboxStatsVO stats = outboxMapper.stats();
        return stats == null ? OutboxStatsVO.builder()
                .pending(0L).processing(0L).processed(0L).dead(0L).build() : stats;
    }

    @Override
    public void retryDead(Long id) {
        OrderEventOutbox event = outboxMapper.getById(id);
        if (event == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        if (outboxMapper.resetDead(id, expirationPolicy.now()) == 0) {
            throw new BaseException(ErrorCode.BUSINESS_ERROR, "只有DEAD事件可以重试");
        }
    }

    private String writePayload(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException ex) {
            throw new BaseException(ErrorCode.INTERNAL_ERROR, "订单事件序列化失败");
        }
    }
}
