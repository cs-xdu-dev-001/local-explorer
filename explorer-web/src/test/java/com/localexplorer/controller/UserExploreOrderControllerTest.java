package com.localexplorer.controller;

import com.localexplorer.context.BaseContext;
import com.localexplorer.controller.user.ExploreOrderController;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.service.ExploreOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserExploreOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExploreOrderService orderService;

    @BeforeEach
    void setUp() {
        ExploreOrderController controller = new ExploreOrderController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void cancelEndpointPassesCurrentUserIdToService() throws Exception {
        BaseContext.setCurrentId(31L);

        mockMvc.perform(put("/user/explore-order/9/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(orderService).cancelByUser(9L, 31L);
    }

    @Test
    void detailEndpointPassesCurrentUserIdToService() throws Exception {
        BaseContext.setCurrentId(31L);

        mockMvc.perform(get("/user/explore-order/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(orderService).getByIdForUser(9L, 31L);
    }

    @Test
    void createRejectsZeroPeopleBeforeCallingService() throws Exception {
        BaseContext.setCurrentId(31L);

        performCreate("0", "张三", "13800001111", "2099-01-01T10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("预约人数必须大于0"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createRejectsBlankContactNameBeforeCallingService() throws Exception {
        BaseContext.setCurrentId(31L);

        performCreate("2", "  ", "13800001111", "2099-01-01T10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("联系人不能为空"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createRejectsInvalidPhoneBeforeCallingService() throws Exception {
        BaseContext.setCurrentId(31L);

        performCreate("2", "张三", "123", "2099-01-01T10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("请输入正确的11位手机号"));

        verifyNoInteractions(orderService);
    }

    @Test
    void createRejectsPastReserveTimeBeforeCallingService() throws Exception {
        BaseContext.setCurrentId(31L);

        performCreate("2", "张三", "13800001111", "2020-01-01T10:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("预约时间必须晚于当前时间"));

        verifyNoInteractions(orderService);
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            String peopleCount, String contactName, String contactPhone, String reserveTime) throws Exception {
        String body = String.format(
                "{\"orderType\":1,\"itemId\":1001,\"peopleCount\":%s," +
                        "\"contactName\":\"%s\",\"contactPhone\":\"%s\"," +
                        "\"reserveTime\":\"%s\"}",
                peopleCount, contactName, contactPhone, reserveTime);
        return mockMvc.perform(post("/user/explore-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }
}
