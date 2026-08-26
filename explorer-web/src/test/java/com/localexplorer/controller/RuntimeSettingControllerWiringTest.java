package com.localexplorer.controller;

import com.localexplorer.service.RuntimeSettingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeSettingControllerWiringTest {

    @Test
    void merchantAndShopControllersInjectRuntimeSettingService() throws Exception {
        assertRuntimeSettingAutowired(com.localexplorer.controller.user.MerchantController.class);
        assertRuntimeSettingAutowired(com.localexplorer.controller.admin.MerchantController.class);
        assertRuntimeSettingAutowired(com.localexplorer.controller.user.ShopController.class);
        assertRuntimeSettingAutowired(com.localexplorer.controller.admin.ShopController.class);
    }

    private void assertRuntimeSettingAutowired(Class<?> controllerType) throws Exception {
        Field field = controllerType.getDeclaredField("runtimeSettingService");
        assertEquals(RuntimeSettingService.class, field.getType());
        assertNotNull(field.getAnnotation(Autowired.class), controllerType.getName());
    }
}
