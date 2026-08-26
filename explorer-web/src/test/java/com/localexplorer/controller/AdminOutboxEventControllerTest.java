package com.localexplorer.controller;

import com.localexplorer.constant.ErrorCode;
import com.localexplorer.dto.OutboxPageQueryDTO;
import com.localexplorer.exception.BaseException;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.vo.OutboxStatsVO;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminOutboxEventControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderEventOutboxService outboxService;

    @BeforeEach
    void setUp() {
        com.localexplorer.controller.admin.OutboxEventController controller =
                new com.localexplorer.controller.admin.OutboxEventController();
        ReflectionTestUtils.setField(controller, "outboxService", outboxService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void pageEndpointPassesOperationalFiltersToService() throws Exception {
        when(outboxService.pageQuery(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageResult(0, Collections.emptyList()));

        mockMvc.perform(get("/admin/outbox-event/page?page=2&pageSize=10" +
                        "&status=DEAD&eventType=ORDER_CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        ArgumentCaptor<OutboxPageQueryDTO> captor = ArgumentCaptor.forClass(OutboxPageQueryDTO.class);
        verify(outboxService).pageQuery(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getStatus()).isEqualTo("DEAD");
        assertThat(captor.getValue().getEventType()).isEqualTo("ORDER_CONFIRMED");
    }

    @Test
    void statsEndpointReturnsEveryOutboxState() throws Exception {
        when(outboxService.stats()).thenReturn(OutboxStatsVO.builder()
                .pending(1L).processing(2L).processed(3L).dead(4L).build());

        mockMvc.perform(get("/admin/outbox-event/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.processing").value(2))
                .andExpect(jsonPath("$.data.processed").value(3))
                .andExpect(jsonPath("$.data.dead").value(4));
    }

    @Test
    void retryEndpointDelegatesDeadEventId() throws Exception {
        mockMvc.perform(put("/admin/outbox-event/17/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(outboxService).retryDead(17L);
    }

    @Test
    void pageRejectsUnknownStatusBeforeServiceCall() throws Exception {
        mockMvc.perform(get("/admin/outbox-event/page?status=UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.getCode()))
                .andExpect(jsonPath("$.msg").value("事件状态不合法"));

        verifyNoInteractions(outboxService);
    }

    @Test
    void retryUsesStableBusinessErrorCodeForNonDeadEvent() throws Exception {
        doThrow(new BaseException(ErrorCode.BUSINESS_ERROR, "只有DEAD事件可以重试"))
                .when(outboxService).retryDead(17L);

        mockMvc.perform(put("/admin/outbox-event/17/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.BUSINESS_ERROR.getCode()))
                .andExpect(jsonPath("$.msg").value("只有DEAD事件可以重试"));
    }
}
