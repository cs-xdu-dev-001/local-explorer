package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserNotificationMapper {

    int insertIgnore(UserNotification notification);

    Page<UserNotification> pageQuery(NotificationPageQueryDTO dto);

    long countUnread(Long userId);

    int markRead(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("now") LocalDateTime now);

    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    UserNotification getById(Long id);
}
