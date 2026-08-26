package com.localexplorer.controller;

import com.localexplorer.context.BaseContext;
import com.localexplorer.constant.ErrorCode;
import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.entity.UserNotification;
import com.localexplorer.exception.BaseException;
import com.localexplorer.filter.RequestTracingFilter;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.UserNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserNotificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserNotificationService notificationService;

    @BeforeEach
    void setUp() {
        com.localexplorer.controller.user.UserNotificationController controller =
                new com.localexplorer.controller.user.UserNotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestTracingFilter())
                .build();
        BaseContext.setCurrentId(31L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void pageEndpointScopesQueryToCurrentUserAndEchoesRequestId() throws Exception {
        UserNotification notification = UserNotification.builder().id(9L).title("预约已确认").build();
        when(notificationService.pageQuery(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(31L)))
                .thenReturn(new PageResult(1, Collections.singletonList(notification)));

        mockMvc.perform(get("/user/notification/page?page=2&pageSize=5")
                        .header(RequestTracingFilter.REQUEST_ID_HEADER, "notification-trace-01"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestTracingFilter.REQUEST_ID_HEADER, "notification-trace-01"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(9));

        ArgumentCaptor<NotificationPageQueryDTO> captor =
                ArgumentCaptor.forClass(NotificationPageQueryDTO.class);
        verify(notificationService).pageQuery(captor.capture(), org.mockito.ArgumentMatchers.eq(31L));
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void unreadCountEndpointReturnsCurrentUsersCount() throws Exception {
        when(notificationService.countUnread(31L)).thenReturn(4L);

        mockMvc.perform(get("/user/notification/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
    }

    @Test
    void markReadAndMarkAllReadUseCurrentUser() throws Exception {
        mockMvc.perform(put("/user/notification/9/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));
        mockMvc.perform(put("/user/notification/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(notificationService).markRead(9L, 31L);
        verify(notificationService).markAllRead(31L);
    }

    @Test
    void pageRejectsOversizedPageBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/user/notification/page?pageSize=101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("每页数量不能超过100"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void markReadHidesOtherUsersNotificationAsNotFound() throws Exception {
        doThrow(new BaseException(ErrorCode.NOT_FOUND, "通知不存在"))
                .when(notificationService).markRead(9L, 31L);

        mockMvc.perform(put("/user/notification/9/read"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.msg").value("通知不存在"));
    }
}
