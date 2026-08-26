package com.localexplorer.controller;

import com.localexplorer.controller.admin.EmployeeController;
import com.localexplorer.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminEmployeeControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "employeeService", employeeService);
        mockMvc = MockMvcBuilders.standaloneSetup(employeeController).build();
    }

    @Test
    void deleteEndpointCallsService() throws Exception {
        mockMvc.perform(delete("/admin/employee").param("id", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(employeeService).deleteById(8L);
    }
}
