package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.entity.UserNotification;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.UserNotificationMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.UserNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class UserNotificationServiceImpl implements UserNotificationService {

    @Autowired
    private UserNotificationMapper notificationMapper;

    @Override
    public PageResult pageQuery(NotificationPageQueryDTO dto, Long userId) {
        dto.setUserId(userId);
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<UserNotification> page = notificationMapper.pageQuery(dto);
        return new PageResult(page.getTotal(), page.getResult());
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional
    public void markRead(Long id, Long userId) {
        UserNotification notification = notificationMapper.getById(id);
        if (notification == null || !Objects.equals(notification.getUserId(), userId)) {
            throw new BaseException(ErrorCode.NOT_FOUND, "通知不存在");
        }
        if (Integer.valueOf(1).equals(notification.getReadStatus())) {
            return;
        }
        if (notificationMapper.markRead(id, userId, LocalDateTime.now()) == 0) {
            throw new BaseException(ErrorCode.BUSINESS_ERROR, "通知状态已变化，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId, LocalDateTime.now());
    }
}
