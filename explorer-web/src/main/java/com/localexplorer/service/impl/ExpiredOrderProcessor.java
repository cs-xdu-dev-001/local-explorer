package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.domain.ExploreOrderStatus;
import com.localexplorer.domain.OrderCancelType;
import com.localexplorer.domain.OrderEventType;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class ExpiredOrderProcessor {

    private static final String EXPIRED_REASON = "待确认时间已超过系统保留期限";

    @Autowired private ExploreOrderMapper orderMapper;
    @Autowired private ExploreItemMapper itemMapper;
    @Autowired private ExplorePackageMapper packageMapper;
    @Autowired private OrderEventOutboxService outboxService;
    @Autowired private OrderExpirationPolicy expirationPolicy;
    @Autowired private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long orderId, LocalDateTime now) {
        ExploreOrder order = orderMapper.getById(orderId);
        if (order == null
                || !Integer.valueOf(ExploreOrderStatus.PENDING.getCode()).equals(order.getStatus())
                || !expirationPolicy.isExpired(order.getExpireAt(), now)) {
            return false;
        }
        int updated = orderMapper.expireIfDue(orderId,
                ExploreOrderStatus.PENDING.getCode(), ExploreOrderStatus.EXPIRED.getCode(),
                OrderCancelType.TIMEOUT, EXPIRED_REASON, now);
        if (updated == 0) {
            return false;
        }
        releaseCapacity(order);
        order.setStatus(ExploreOrderStatus.EXPIRED.getCode());
        order.setCancelType(OrderCancelType.TIMEOUT);
        order.setCancelReason(EXPIRED_REASON);
        order.setUpdateTime(now);
        outboxService.append(order, OrderEventType.EXPIRED, now);
        invalidateCapacity(order);
        return true;
    }

    private void invalidateCapacity(ExploreOrder order) {
        CacheInvalidation.Builder invalidation = CacheInvalidation.builder();
        if (Integer.valueOf(1).equals(order.getOrderType()) && order.getItemId() != null) {
            invalidation.clear(HotCacheDomain.ITEM_LIST)
                    .evict(HotCacheDomain.ITEM_DETAIL, order.getItemId());
        } else if (Integer.valueOf(2).equals(order.getOrderType()) && order.getPackageId() != null) {
            invalidation.clear(HotCacheDomain.PACKAGE_LIST)
                    .evict(HotCacheDomain.PACKAGE_DETAIL, order.getPackageId());
        }
        cacheInvalidationCoordinator.invalidate(invalidation.build());
    }

    private void releaseCapacity(ExploreOrder order) {
        int released = 0;
        int peopleCount = order.getPeopleCount() == null ? 1 : order.getPeopleCount();
        if (Integer.valueOf(1).equals(order.getOrderType()) && order.getItemId() != null) {
            released = itemMapper.releaseCapacity(order.getItemId(), peopleCount);
        } else if (Integer.valueOf(2).equals(order.getOrderType()) && order.getPackageId() != null) {
            released = packageMapper.releaseCapacity(order.getPackageId(), peopleCount);
        }
        if (released != 1) {
            throw new BaseException(ErrorCode.BUSINESS_ERROR, "预约容量释放失败，订单已回滚");
        }
    }
}
