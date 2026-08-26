package com.localexplorer.controller;

import com.localexplorer.controller.admin.UserManageController;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.service.UserInteractionService;
import com.localexplorer.service.UserService;
import com.localexplorer.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserManageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ExploreOrderService orderService;

    @Mock
    private UserInteractionService interactionService;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        UserManageController controller = new UserManageController();
        ReflectionTestUtils.setField(controller, "userMapper", userMapper);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "interactionService", interactionService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void resetPasswordEndpointCallsService() throws Exception {
        mockMvc.perform(put("/admin/user-manage/7/password/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(userService).resetPassword(7L);
    }

    @Test
    void statusEndpointCallsService() throws Exception {
        mockMvc.perform(post("/admin/user-manage/status/0").param("id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(userService).startOrStop(0, 7L);
    }

    @Test
    void updateEndpointCopiesPathIdAndCallsService() throws Exception {
        mockMvc.perform(put("/admin/user-manage/7")
                        .contentType("application/json")
                        .content("{\"name\":\"林夏\",\"phone\":\"13800001111\",\"sex\":\"0\",\"idNumber\":\"330100199901010011\",\"avatar\":\"https://example.com/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        UserDTO expected = new UserDTO();
        expected.setId(7L);
        expected.setName("林夏");
        expected.setPhone("13800001111");
        expected.setSex("0");
        expected.setIdNumber("330100199901010011");
        expected.setAvatar("https://example.com/a.png");
        verify(userService).update(expected);
    }
}
