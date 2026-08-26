package com.localexplorer.service;

import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheTestDoubles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.RuntimeSettingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeSettingServiceTest {

    private RuntimeSettingService service;
    private RuntimeSettingMapper settingMapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        settingMapper = mock(RuntimeSettingMapper.class);
        objectMapper = new ObjectMapper();
        service = createService(settingMapper);
    }

    @Test
    void returnsDefaultShopStatusWhenDatabaseHasNoSetting() {
        when(settingMapper.selectValue(RuntimeSettingService.SHOP_STATUS_KEY)).thenReturn(null);

        assertEquals(0, service.getShopStatus());
    }

    @Test
    void readsShopStatusFromDatabaseAfterRestart() {
        when(settingMapper.selectValue(RuntimeSettingService.SHOP_STATUS_KEY)).thenReturn("1");

        assertEquals(1, service.getShopStatus());
    }

    @Test
    void persistsShopStatusBeforeReturningSuccess() {
        service.setShopStatus(1);

        verify(settingMapper).upsert(RuntimeSettingService.SHOP_STATUS_KEY, "1");
    }

    @Test
    void rejectsInvalidShopStatus() {
        BaseException error = assertThrows(BaseException.class, () -> service.setShopStatus(2));

        assertEquals("状态参数只能为0或1", error.getMessage());
        verify(settingMapper, org.mockito.Mockito.never())
                .upsert(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doesNotPretendShopStatusWasSavedOrServeUnboundedFallbackWhenDatabaseFails() {
        doThrow(new RuntimeException("database down"))
                .when(settingMapper).upsert(RuntimeSettingService.SHOP_STATUS_KEY, "1");
        when(settingMapper.selectValue(RuntimeSettingService.SHOP_STATUS_KEY))
                .thenThrow(new RuntimeException("database down"));

        assertThrows(RuntimeException.class, () -> service.setShopStatus(1));
        assertThrows(RuntimeException.class, service::getShopStatus);
    }

    @Test
    void returnsDefaultMerchantInfoWhenDatabaseHasNoSetting() {
        when(settingMapper.selectValue(RuntimeSettingService.MERCHANT_INFO_KEY)).thenReturn(null);

        MerchantInfoDTO info = service.getMerchantInfo();

        assertNotNull(info.getName());
        assertNotNull(info.getSlogan());
    }

    @Test
    void persistsMerchantInfoAndReloadsItInANewServiceInstance() {
        MerchantInfoDTO update = new MerchantInfoDTO();
        update.setName("Audit Merchant");
        update.setSlogan("Durable settings");

        service.setMerchantInfo(update);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(settingMapper).upsert(
                org.mockito.ArgumentMatchers.eq(RuntimeSettingService.MERCHANT_INFO_KEY),
                valueCaptor.capture()
        );

        RuntimeSettingMapper restartedMapper = mock(RuntimeSettingMapper.class);
        when(restartedMapper.selectValue(RuntimeSettingService.MERCHANT_INFO_KEY))
                .thenReturn(valueCaptor.getValue());
        RuntimeSettingService restartedService = createService(restartedMapper);

        MerchantInfoDTO reloaded = restartedService.getMerchantInfo();
        assertEquals("Audit Merchant", reloaded.getName());
        assertEquals("Durable settings", reloaded.getSlogan());
    }

    @Test
    void doesNotReplaceLastGoodMerchantInfoWithMalformedStoredJson() {
        when(settingMapper.selectValue(RuntimeSettingService.MERCHANT_INFO_KEY)).thenReturn("not-json");

        MerchantInfoDTO info = service.getMerchantInfo();

        assertNotNull(info.getName());
        assertNotNull(info.getSlogan());
    }

    private RuntimeSettingService createService(RuntimeSettingMapper mapper) {
        RuntimeSettingService runtimeSettingService = new RuntimeSettingService();
        ReflectionTestUtils.setField(runtimeSettingService, "runtimeSettingMapper", mapper);
        ReflectionTestUtils.setField(runtimeSettingService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(runtimeSettingService, "hotCache", HotCacheTestDoubles.passThroughCache());
        ReflectionTestUtils.setField(runtimeSettingService, "cacheInvalidationCoordinator",
                mock(CacheInvalidationCoordinator.class));
        return runtimeSettingService;
    }
}
