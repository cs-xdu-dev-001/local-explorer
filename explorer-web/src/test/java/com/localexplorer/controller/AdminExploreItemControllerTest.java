package com.localexplorer.controller;

import com.localexplorer.controller.admin.ExploreItemController;
import com.localexplorer.controller.admin.ExplorePackageController;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.service.ExplorePackageService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminExploreItemControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExploreItemService itemService;

    @Mock
    private ExplorePackageService packageService;

    @BeforeEach
    void setUp() {
        ExploreItemController itemController = new ExploreItemController();
        ReflectionTestUtils.setField(itemController, "itemService", itemService);

        mockMvc = MockMvcBuilders.standaloneSetup(itemController).build();
    }

    @Test
    void itemStatusEndpointCallsService() throws Exception {
        mockMvc.perform(post("/admin/explore-item/status/0").param("id", "17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(itemService).startOrStop(0, 17L);
    }

    @Test
    void packageStatusEndpointCallsService() throws Exception {
        ExplorePackageController packageController = new ExplorePackageController();
        ReflectionTestUtils.setField(packageController, "packageService", packageService);
        MockMvc packageMockMvc = MockMvcBuilders.standaloneSetup(packageController).build();

        packageMockMvc.perform(post("/admin/explore-package/status/0").param("id", "27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(packageService).startOrStop(0, 27L);
    }
}
