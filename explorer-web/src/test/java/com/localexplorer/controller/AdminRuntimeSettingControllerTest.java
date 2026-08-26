package com.localexplorer.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.localexplorer.annotation.OperationLog;
import com.localexplorer.controller.admin.MerchantController;
import com.localexplorer.controller.admin.ShopController;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.service.RuntimeSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminRuntimeSettingControllerTest {

    private MockMvc shopMockMvc;
    private MockMvc merchantMockMvc;

    @Mock
    private RuntimeSettingService runtimeSettingService;

    @BeforeEach
    void setUp() {
        ShopController shopController = new ShopController();
        ReflectionTestUtils.setField(shopController, "runtimeSettingService", runtimeSettingService);
        shopMockMvc = MockMvcBuilders.standaloneSetup(shopController).build();

        MerchantController merchantController = new MerchantController();
        ReflectionTestUtils.setField(merchantController, "runtimeSettingService", runtimeSettingService);
        merchantMockMvc = MockMvcBuilders.standaloneSetup(merchantController).build();
    }

    @Test
    void shopStatusEndpointSetsRuntimeStatus() throws Exception {
        shopMockMvc.perform(put("/admin/shop/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1));

        verify(runtimeSettingService).setShopStatus(1);
    }

    @Test
    void shopStatusEndpointReadsRuntimeStatus() throws Exception {
        when(runtimeSettingService.getShopStatus()).thenReturn(1);

        shopMockMvc.perform(get("/admin/shop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void merchantInfoEndpointPersistsRuntimeInfo() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(MerchantController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            merchantMockMvc.perform(put("/admin/merchant/info")
                            .contentType("application/json")
                            .content("{\"name\":\"Local Explorer\",\"slogan\":\"Curated city experiences\",\"phone\":\"029-88888888\",\"address\":\"Xi'an\",\"businessHours\":\"10:00-22:00\",\"notice\":\"Book ahead\",\"coverImage\":\"/assets/images/citywalk.svg\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(1));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .allSatisfy(message -> assertThat(message)
                        .doesNotContain("029-88888888", "Xi'an", "Book ahead"));

        MerchantInfoDTO expected = new MerchantInfoDTO();
        expected.setName("Local Explorer");
        expected.setSlogan("Curated city experiences");
        expected.setPhone("029-88888888");
        expected.setAddress("Xi'an");
        expected.setBusinessHours("10:00-22:00");
        expected.setNotice("Book ahead");
        expected.setCoverImage("/assets/images/citywalk.svg");
        verify(runtimeSettingService).setMerchantInfo(expected);
    }

    @Test
    void merchantInfoEndpointReturnsRuntimeInfo() throws Exception {
        MerchantInfoDTO dto = new MerchantInfoDTO();
        dto.setName("Local Explorer");
        dto.setSlogan("Curated city experiences");
        when(runtimeSettingService.getMerchantInfo()).thenReturn(dto);

        merchantMockMvc.perform(get("/admin/merchant/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.name").value("Local Explorer"))
                .andExpect(jsonPath("$.data.slogan").value("Curated city experiences"));
    }

    @Test
    void runtimeSettingWritesAreAudited() throws Exception {
        assertOperationLog(
                ShopController.class.getMethod("setStatus", Integer.class),
                "切换门店营业状态"
        );
        assertOperationLog(
                MerchantController.class.getMethod("updateInfo", MerchantInfoDTO.class),
                "修改商户资料"
        );
    }

    private void assertOperationLog(Method method, String description) {
        OperationLog operationLog = method.getAnnotation(OperationLog.class);
        assertNotNull(operationLog, method.getName() + " should be audited");
        assertEquals(description, operationLog.value());
    }
}
