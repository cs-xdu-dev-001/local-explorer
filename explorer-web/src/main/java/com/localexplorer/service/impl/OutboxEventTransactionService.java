package com.localexplorer.service.impl;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.domain.OrderEventType;
import com.localexplorer.entity.OrderEventOutbox;
import com.localexplorer.entity.UserNotification;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.mapper.UserNotificationMapper;
import com.localexplorer.service.OutboxRetryPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OutboxEventTransactionService {

    @Autowired private OrderEventOutboxMapper outboxMapper;
    @Autowired private UserNotificationMapper notificationMapper;
    @Autowired private OutboxRetryPolicy retryPolicy;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long eventId, String lockToken, LocalDateTime now, LocalDateTime lockedUntil) {
        return outboxMapper.claim(eventId, lockToken, now, lockedUntil) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long eventId, String lockToken, LocalDateTime now) {
        long started = System.nanoTime();
        OrderEventOutbox event = requireEvent(eventId);
        UserNotification notification = UserNotification.builder()
                .eventId(event.getEventId())
                .userId(event.getUserId())
                .orderId(event.getAggregateId())
                .notificationType(event.getEventType())
                .title(title(event.getEventType()))
                .content(content(event.getEventType()))
                .readStatus(0)
                .createTime(now)
                .build();
        notificationMapper.insertIgnore(notification);
        if (outboxMapper.markProcessed(eventId, lockToken, now) != 1) {
            throw new BaseException(ErrorCode.BUSINESS_ERROR, "事件租约已失效，处理结果已回滚");
        }
        log.info("Outbox delivery eventId={} orderId={} result=processed elapsedMs={}",
                event.getEventId(), event.getAggregateId(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long eventId, String lockToken,
                                 RuntimeException failure, LocalDateTime now) {
        long started = System.nanoTime();
        OrderEventOutbox event = requireEvent(eventId);
        int retryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        String error = retryPolicy.sanitizeError(failure.getMessage());
        if (retryPolicy.shouldMarkDead(retryCount)) {
            if (outboxMapper.markDead(eventId, lockToken, retryCount, error, now) != 1) {
                throw new BaseException(ErrorCode.BUSINESS_ERROR, "事件租约已失效，失败状态未记录");
            }
            log.warn("Outbox delivery eventId={} orderId={} result=dead retryCount={} elapsedMs={}",
                    event.getEventId(), event.getAggregateId(), retryCount,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return true;
        }
        if (outboxMapper.markRetry(eventId, lockToken, retryCount,
                retryPolicy.nextRetryAt(now, retryCount), error, now) != 1) {
            throw new BaseException(ErrorCode.BUSINESS_ERROR, "事件租约已失效，失败状态未记录");
        }
        log.warn("Outbox delivery eventId={} orderId={} result=retry retryCount={} elapsedMs={}",
                event.getEventId(), event.getAggregateId(), retryCount,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return false;
    }

    private OrderEventOutbox requireEvent(Long id) {
        OrderEventOutbox event = outboxMapper.getById(id);
        if (event == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "事件不存在");
        }
        return event;
    }

    private String title(String eventType) {
        if (OrderEventType.CONFIRMED.equals(eventType)) {
            return "预约已确认";
        }
        if (OrderEventType.COMPLETED.equals(eventType)) {
            return "预约已完成";
        }
        if (OrderEventType.EXPIRED.equals(eventType)) {
            return "预约已超时取消";
        }
        return "预约已取消";
    }

    private String content(String eventType) {
        if (OrderEventType.CONFIRMED.equals(eventType)) {
            return "商家已确认您的预约，点击查看预约详情。";
        }
        if (OrderEventType.COMPLETED.equals(eventType)) {
            return "本次预约已完成，欢迎分享您的体验。";
        }
        if (OrderEventType.EXPIRED.equals(eventType)) {
            return "预约长时间未确认，系统已取消并释放名额。";
        }
        return "预约已取消，相关名额已经释放。";
    }
}
