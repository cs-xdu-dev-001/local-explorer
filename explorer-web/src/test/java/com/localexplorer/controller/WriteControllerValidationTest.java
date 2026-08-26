package com.localexplorer.controller;

import com.localexplorer.controller.admin.MerchantController;
import com.localexplorer.controller.admin.UserManageController;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.service.ReviewService;
import com.localexplorer.service.RuntimeSettingService;
import com.localexplorer.service.UserService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WriteControllerValidationTest {

    private MockMvc mockMvc;

    @Mock
    private ReviewService reviewService;
    @Mock
    private RuntimeSettingService runtimeSettingService;
    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        com.localexplorer.controller.user.ReviewController userReviewController =
                new com.localexplorer.controller.user.ReviewController();
        ReflectionTestUtils.setField(userReviewController, "reviewService", reviewService);

        com.localexplorer.controller.admin.ReviewController adminReviewController =
                new com.localexplorer.controller.admin.ReviewController();
        ReflectionTestUtils.setField(adminReviewController, "reviewService", reviewService);

        MerchantController merchantController = new MerchantController();
        ReflectionTestUtils.setField(merchantController, "runtimeSettingService", runtimeSettingService);

        UserManageController userManageController = new UserManageController();
        ReflectionTestUtils.setField(userManageController, "userService", userService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        userReviewController,
                        adminReviewController,
                        merchantController,
                        userManageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void userReviewRejectsInvalidRatingBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/user/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":3001,\"rating\":6,\"content\":\"good\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("评分必须在1-5之间"));

        verifyNoInteractions(reviewService);
    }

    @Test
    void reviewReplyRejectsOversizedContentBeforeServiceCall() throws Exception {
        String reply = String.join("", java.util.Collections.nCopies(501, "x"));
        mockMvc.perform(put("/admin/review/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"replyContent\":\"" + reply + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("回复内容不能超过500个字符"));

        verifyNoInteractions(reviewService);
    }

    @Test
    void merchantUpdateRejectsBlankNameBeforePersistence() throws Exception {
        mockMvc.perform(put("/admin/merchant/info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"phone\":\"13800001111\",\"address\":\"中心广场\",\"businessHours\":\"09:00-21:00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("商户名称不能为空"));

        verifyNoInteractions(runtimeSettingService);
    }

    @Test
    void userUpdateRejectsMalformedPhoneBeforeServiceCall() throws Exception {
        mockMvc.perform(put("/admin/user-manage/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"张三\",\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("请输入正确的11位手机号"));

        verifyNoInteractions(userService);
    }
}
