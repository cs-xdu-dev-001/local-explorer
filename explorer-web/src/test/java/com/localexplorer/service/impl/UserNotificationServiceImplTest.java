package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.entity.UserNotification;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.UserNotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceImplTest {

    private UserNotificationServiceImpl service;
    @Mock private UserNotificationMapper notificationMapper;
    @Mock private Page<UserNotification> notificationPage;

    @BeforeEach
    void setUp() {
        service = new UserNotificationServiceImpl();
        ReflectionTestUtils.setField(service, "notificationMapper", notificationMapper);
    }

    @Test
    void pageAlwaysScopesQueryToAuthenticatedUser() {
        NotificationPageQueryDTO dto = new NotificationPageQueryDTO();
        dto.setUserId(99L);
        when(notificationMapper.pageQuery(any())).thenReturn(notificationPage);

        service.pageQuery(dto, 7L);

        verify(notificationMapper).pageQuery(org.mockito.ArgumentMatchers.argThat(query ->
                Long.valueOf(7L).equals(query.getUserId())));
    }

    @Test
    void userCannotMarkAnotherUsersNotificationRead() {
        UserNotification notification = UserNotification.builder()
                .id(9L)
                .userId(99L)
                .readStatus(0)
                .build();
        when(notificationMapper.getById(9L)).thenReturn(notification);

        assertThatThrownBy(() -> service.markRead(9L, 7L))
                .isInstanceOf(BaseException.class);
        verify(notificationMapper, never()).markRead(any(), any(), any());
    }
}
