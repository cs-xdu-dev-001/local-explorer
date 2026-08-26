package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.ExploreOrderDTO;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.domain.ExploreOrderStatus;
import com.localexplorer.domain.OrderCancelType;
import com.localexplorer.domain.OrderEventType;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreItemMapper;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.ExplorePackageMapper;
import com.localexplorer.metrics.BookingMetrics;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.RuntimeSettingService;
import com.localexplorer.vo.ExploreOrderVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class ExploreOrderServiceImpl implements ExploreOrderService {

    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private ExploreItemMapper itemMapper;
    @Autowired
    private ExplorePackageMapper packageMapper;
    @Autowired
    private RuntimeSettingService runtimeSettingService;
    @Autowired
    private BookingMetrics bookingMetrics;
    @Autowired
    private OrderExpirationPolicy expirationPolicy;
    @Autowired
    private OrderEventOutboxService outboxService;
    @Autowired
    private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.currentTimeMillis() % 10000);

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Long create(ExploreOrderDTO dto, Long userId) {
        try {
            Long orderId = createOrder(dto, userId);
            invalidateCapacity(dto.getOrderType(), dto.getItemId(), dto.getPackageId());
            return orderId;
        } catch (RuntimeException ex) {
            bookingMetrics.recordFailure(classifyFailure(ex));
            throw ex;
        }
    }

    private Long createOrder(ExploreOrderDTO dto, Long userId) {
        String requestId = normalizeRequestId(dto.getRequestId());
        ExploreOrder existingOrder = findExistingOrder(userId, requestId);
        if (existingOrder != null) {
            bookingMetrics.recordIdempotentHit();
            return existingOrder.getId();
        }

        Integer peopleCount = dto.getPeopleCount() != null ? dto.getPeopleCount() : 1;
        if (peopleCount <= 0) {
            throw new BaseException("\u9884\u7ea6\u4eba\u6570\u5fc5\u987b\u5927\u4e8e0");
        }
        assertShopOpen();

        String itemName;
        BigDecimal amount;
        if (Integer.valueOf(1).equals(dto.getOrderType())) {
            ExploreItem item = getAvailableItem(dto.getItemId(), peopleCount);
            reserveItemCapacity(item.getId(), peopleCount);
            itemName = item.getName();
            amount = item.getPrice();
        } else if (Integer.valueOf(2).equals(dto.getOrderType())) {
            ExplorePackage packageEntity = getAvailablePackage(dto.getPackageId(), peopleCount);
            reservePackageCapacity(packageEntity.getId(), peopleCount);
            itemName = packageEntity.getName();
            amount = packageEntity.getPrice();
        } else {
            throw new BaseException("\u9884\u7ea6\u7c7b\u578b\u4e0d\u6b63\u786e");
        }

        LocalDateTime now = expirationPolicy.now();
        ExploreOrder order = ExploreOrder.builder()
                .userId(userId)
                .orderNo(generateOrderNo())
                .orderType(dto.getOrderType())
                .itemId(dto.getItemId())
                .packageId(dto.getPackageId())
                .itemName(itemName)
                .amount(amount)
                .peopleCount(peopleCount)
                .contactName(dto.getContactName())
                .contactPhone(dto.getContactPhone())
                .reserveTime(dto.getReserveTime())
                .requestId(requestId)
                .expireAt(expirationPolicy.newDeadline())
                .remark(dto.getRemark() != null ? dto.getRemark() : "")
                .status(ExploreOrderStatus.PENDING.getCode())
                .createTime(now)
                .updateTime(now)
                .build();
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ex) {
            releaseBooked(order);
            ExploreOrder concurrentExistingOrder = findExistingOrder(userId, requestId);
            if (concurrentExistingOrder != null) {
                bookingMetrics.recordIdempotentHit();
                return concurrentExistingOrder.getId();
            }
            throw ex;
        }
        bookingMetrics.recordCreated(resourceType(dto.getOrderType()));
        log.info("User {} created order {}", userId, order.getOrderNo());
        return order.getId();
    }

    @Override
    public PageResult pageQuery(ExploreOrderPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ExploreOrderVO> page = orderMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public PageResult adminPageQuery(ExploreOrderPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<ExploreOrderVO> page = orderMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public ExploreOrder getById(Long id) {
        ExploreOrder order = orderMapper.getById(id);
        if (order == null) {
            throw new BaseException("预约不存在");
        }
        return order;
    }

    @Override
    public ExploreOrder getByIdForUser(Long id, Long userId) {
        ExploreOrder order = orderMapper.getById(id);
        assertOrderBelongsToUser(order, userId);
        return order;
    }

    @Override
    @Transactional
    public void cancelByUser(Long id, Long userId) {
        ExploreOrder order = orderMapper.getById(id);
        assertOrderBelongsToUser(order, userId);
        updateStatus(order, ExploreOrderStatus.CANCELED.getCode(), OrderCancelType.USER,
                "用户主动取消", OrderEventType.CANCELED_BY_USER);
        invalidateCapacity(order.getOrderType(), order.getItemId(), order.getPackageId());
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        ExploreOrder order = orderMapper.getById(id);
        if (order == null) {
            throw new BaseException("\u9884\u7ea6\u4e0d\u5b58\u5728");
        }
        if (Integer.valueOf(ExploreOrderStatus.EXPIRED.getCode()).equals(status)) {
            throw new BaseException("系统超时状态只能由超时任务设置");
        }
        String cancelType = Integer.valueOf(ExploreOrderStatus.CANCELED.getCode()).equals(status)
                ? OrderCancelType.ADMIN : null;
        String cancelReason = cancelType == null ? null : "管理员取消预约";
        updateStatus(order, status, cancelType, cancelReason, adminEventType(status));
        if (Integer.valueOf(ExploreOrderStatus.CANCELED.getCode()).equals(status)) {
            invalidateCapacity(order.getOrderType(), order.getItemId(), order.getPackageId());
        }
    }

    private void updateStatus(ExploreOrder order,
                              Integer status,
                              String cancelType,
                              String cancelReason,
                              String eventType) {
        validateStatusTransition(order.getStatus(), status);
        if (status.equals(order.getStatus())) {
            return;
        }
        ExploreOrderStatus target = ExploreOrderStatus.fromCode(status);
        boolean shouldReleaseBooked = target == ExploreOrderStatus.CANCELED
                || target == ExploreOrderStatus.EXPIRED;
        LocalDateTime now = expirationPolicy.now();
        int updated = orderMapper.updateStatusIfCurrent(
                order.getId(), order.getStatus(), status, cancelType, cancelReason, now);
        if (updated == 0) {
            ExploreOrder latest = orderMapper.getById(order.getId());
            if (latest == null) {
                throw new BaseException("预约不存在");
            }
            if (status.equals(latest.getStatus())) {
                return;
            }
            throw new BaseException("预约状态已发生变化，请刷新后重试");
        }
        if (shouldReleaseBooked) {
            releaseBooked(order);
        }
        order.setStatus(status);
        order.setCancelType(cancelType);
        order.setCancelReason(cancelReason);
        order.setUpdateTime(now);
        if (eventType != null) {
            outboxService.append(order, eventType, now);
        }
        log.info("Order {} status changed to {}", order.getOrderNo(), status);
    }

    @Override
    public Long countByUserId(Long userId) {
        return orderMapper.countByUserId(userId);
    }

    private void assertShopOpen() {
        if (!Integer.valueOf(1).equals(runtimeSettingService.getShopStatus())) {
            throw new BaseException("门店休息中，暂不可预约");
        }
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        return requestId.trim();
    }

    private ExploreOrder findExistingOrder(Long userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        return orderMapper.getByUserIdAndRequestId(userId, requestId);
    }

    private void assertOrderBelongsToUser(ExploreOrder order, Long userId) {
        if (order == null) {
            throw new BaseException("\u9884\u7ea6\u4e0d\u5b58\u5728");
        }
        if (!Objects.equals(order.getUserId(), userId)) {
            throw new BaseException("无权操作该预约");
        }
    }

    private ExploreItem getAvailableItem(Long itemId, Integer peopleCount) {
        if (itemId == null) {
            throw new BaseException("\u9884\u7ea6\u9879\u76ee\u4e0d\u5b58\u5728");
        }
        ExploreItem item = itemMapper.getById(itemId);
        if (item == null) {
            throw new BaseException("\u9884\u7ea6\u9879\u76ee\u4e0d\u5b58\u5728");
        }
        if (!StatusConstant.ENABLE.equals(item.getStatus())) {
            throw new BaseException("\u9884\u7ea6\u9879\u76ee\u5df2\u505c\u7528");
        }
        assertCapacity(item.getCapacity(), item.getBooked(), peopleCount, "item");
        return item;
    }

    private ExplorePackage getAvailablePackage(Long packageId, Integer peopleCount) {
        if (packageId == null) {
            throw new BaseException("\u9884\u7ea6\u5957\u9910\u4e0d\u5b58\u5728");
        }
        ExplorePackage packageEntity = packageMapper.getById(packageId);
        if (packageEntity == null) {
            throw new BaseException("\u9884\u7ea6\u5957\u9910\u4e0d\u5b58\u5728");
        }
        if (!StatusConstant.ENABLE.equals(packageEntity.getStatus())) {
            throw new BaseException("\u9884\u7ea6\u5957\u9910\u5df2\u505c\u7528");
        }
        assertCapacity(packageEntity.getCapacity(), packageEntity.getBooked(), peopleCount, "package");
        return packageEntity;
    }

    private void assertCapacity(Integer capacity, Integer booked, Integer peopleCount, String resourceType) {
        if (capacity != null && (booked == null ? 0 : booked) + peopleCount > capacity) {
            bookingMetrics.recordCapacityExhausted(resourceType);
            throw new BaseException("\u540d\u989d\u4e0d\u8db3");
        }
    }

    private void reserveItemCapacity(Long itemId, Integer peopleCount) {
        if (itemMapper.reserveCapacity(itemId, peopleCount) == 0) {
            bookingMetrics.recordCapacityExhausted("item");
            throw new BaseException("名额不足或项目状态已变化，请刷新后重试");
        }
    }

    private void reservePackageCapacity(Long packageId, Integer peopleCount) {
        if (packageMapper.reserveCapacity(packageId, peopleCount) == 0) {
            bookingMetrics.recordCapacityExhausted("package");
            throw new BaseException("名额不足或套餐状态已变化，请刷新后重试");
        }
    }

    private void releaseBooked(ExploreOrder order) {
        Integer peopleCount = order.getPeopleCount() != null ? order.getPeopleCount() : 1;
        if (peopleCount <= 0) {
            return;
        }
        if (Integer.valueOf(1).equals(order.getOrderType()) && order.getItemId() != null) {
            if (itemMapper.releaseCapacity(order.getItemId(), peopleCount) != 1) {
                throw new BaseException("预约容量释放失败，订单已回滚");
            }
            return;
        }
        if (Integer.valueOf(2).equals(order.getOrderType()) && order.getPackageId() != null) {
            if (packageMapper.releaseCapacity(order.getPackageId(), peopleCount) != 1) {
                throw new BaseException("预约容量释放失败，订单已回滚");
            }
            return;
        }
        throw new BaseException("预约资源信息不完整，容量无法释放");
    }

    private void invalidateCapacity(Integer orderType, Long itemId, Long packageId) {
        CacheInvalidation.Builder invalidation = CacheInvalidation.builder();
        if (Integer.valueOf(1).equals(orderType) && itemId != null) {
            invalidation.clear(HotCacheDomain.ITEM_LIST)
                    .evict(HotCacheDomain.ITEM_DETAIL, itemId);
        } else if (Integer.valueOf(2).equals(orderType) && packageId != null) {
            invalidation.clear(HotCacheDomain.PACKAGE_LIST)
                    .evict(HotCacheDomain.PACKAGE_DETAIL, packageId);
        } else {
            return;
        }
        cacheInvalidationCoordinator.invalidate(invalidation.build());
    }

    private void validateStatusTransition(Integer currentStatus, Integer nextStatus) {
        try {
            ExploreOrderStatus current = ExploreOrderStatus.fromCode(
                    currentStatus == null ? ExploreOrderStatus.PENDING.getCode() : currentStatus);
            ExploreOrderStatus target = ExploreOrderStatus.fromCode(nextStatus);
            if (!current.canTransitionTo(target)) {
                throw new BaseException("订单状态流转不合法");
            }
        } catch (IllegalArgumentException ex) {
            throw new BaseException("订单状态流转不合法");
        }
    }

    private String adminEventType(Integer status) {
        ExploreOrderStatus target;
        try {
            target = ExploreOrderStatus.fromCode(status);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        if (target == ExploreOrderStatus.CONFIRMED) {
            return OrderEventType.CONFIRMED;
        }
        if (target == ExploreOrderStatus.COMPLETED) {
            return OrderEventType.COMPLETED;
        }
        if (target == ExploreOrderStatus.CANCELED) {
            return OrderEventType.CANCELED_BY_ADMIN;
        }
        return null;
    }

    private String classifyFailure(RuntimeException ex) {
        if (ex instanceof DataAccessException) {
            return "database";
        }
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("名额不足")) {
            return "capacity";
        }
        if (message.contains("门店休息")) {
            return "shop_closed";
        }
        if (message.contains("不存在")) {
            return "not_found";
        }
        if (message.contains("停用")) {
            return "disabled";
        }
        if (message.contains("必须") || message.contains("不正确") || message.contains("不合法")) {
            return "validation";
        }
        return ex instanceof BaseException ? "business" : "internal";
    }

    private String resourceType(Integer orderType) {
        if (Integer.valueOf(1).equals(orderType)) {
            return "item";
        }
        if (Integer.valueOf(2).equals(orderType)) {
            return "package";
        }
        return "unknown";
    }

    private String generateOrderNo() {
        return "ORD" + expirationPolicy.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
                + String.format("%04d", ORDER_SEQ.incrementAndGet() % 10000);
    }
}
