package com.localexplorer.controller;

import com.localexplorer.controller.admin.CategoryController;
import com.localexplorer.dto.CategoryDTO;
import com.localexplorer.service.CategoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        CategoryController controller = new CategoryController();
        ReflectionTestUtils.setField(controller, "categoryService", categoryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void statusEndpointCallsService() throws Exception {
        mockMvc.perform(post("/admin/category/status/0").param("id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(categoryService).startOrStop(0, 5L);
    }

    @Test
    void deleteEndpointCallsService() throws Exception {
        mockMvc.perform(delete("/admin/category").param("id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(categoryService).deleteById(5L);
    }

    @Test
    void updateEndpointCallsService() throws Exception {
        mockMvc.perform(put("/admin/category")
                        .contentType("application/json")
                        .content("{\"id\":5,\"type\":1,\"name\":\"City Walk\",\"sort\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        CategoryDTO expected = new CategoryDTO();
        expected.setId(5L);
        expected.setType(1);
        expected.setName("City Walk");
        expected.setSort(10);
        verify(categoryService).update(expected);
    }
}
