package com.localexplorer.controller;

import com.localexplorer.service.ExplorePackageService;
import com.localexplorer.vo.PackageItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserExplorePackageControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExplorePackageService packageService;

    @BeforeEach
    void setUp() {
        com.localexplorer.controller.user.ExplorePackageController packageController =
                new com.localexplorer.controller.user.ExplorePackageController();
        ReflectionTestUtils.setField(packageController, "packageService", packageService);
        mockMvc = MockMvcBuilders.standaloneSetup(packageController).build();
    }

    @Test
    void packageItemsEndpointReturnsPackageItemDetails() throws Exception {
        when(packageService.getPackageItemsById(21L)).thenReturn(Collections.singletonList(
                PackageItemVO.builder()
                        .itemId(1002L)
                        .name("手冲咖啡品鉴")
                        .copies(1)
                        .build()
        ));

        mockMvc.perform(get("/user/explore-package/items/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data[0].itemId").value(1002))
                .andExpect(jsonPath("$.data[0].name").value("手冲咖啡品鉴"))
                .andExpect(jsonPath("$.data[0].copies").value(1));

        verify(packageService).getPackageItemsById(21L);
    }
}
