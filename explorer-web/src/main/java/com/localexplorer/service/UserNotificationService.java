package com.localexplorer.service;

import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.result.PageResult;

public interface UserNotificationService {

    PageResult pageQuery(NotificationPageQueryDTO dto, Long userId);

    long countUnread(Long userId);

    void markRead(Long id, Long userId);

    void markAllRead(Long userId);
}
