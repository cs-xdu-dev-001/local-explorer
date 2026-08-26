package com.localexplorer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.RuntimeSettingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RuntimeSettingService {

    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";
    public static final String MERCHANT_INFO_KEY = "MERCHANT_INFO";

    @Autowired
    private RuntimeSettingMapper runtimeSettingMapper;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private HotReadCacheService hotCache;
    @Autowired
    private CacheInvalidationCoordinator cacheInvalidationCoordinator;

    private volatile Integer fallbackShopStatus = 0;
    private volatile MerchantInfoDTO fallbackMerchantInfo = defaultMerchantInfo();

    public Integer getShopStatus() {
        return hotCache.get(HotCacheDomain.SHOP_STATUS, "current", () -> {
            String value = runtimeSettingMapper.selectValue(SHOP_STATUS_KEY);
            if (value != null) {
                fallbackShopStatus = "1".equals(value) ? 1 : 0;
            }
            return fallbackShopStatus;
        });
    }

    @Transactional
    public void setShopStatus(Integer status) {
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        runtimeSettingMapper.upsert(SHOP_STATUS_KEY, String.valueOf(status));
        fallbackShopStatus = status;
        cacheInvalidationCoordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.SHOP_STATUS)
                .build());
    }

    public MerchantInfoDTO getMerchantInfo() {
        MerchantInfoDTO cached = hotCache.get(HotCacheDomain.MERCHANT_INFO, "current", () -> {
            String value = runtimeSettingMapper.selectValue(MERCHANT_INFO_KEY);
            if (value != null && !value.trim().isEmpty()) {
                try {
                    fallbackMerchantInfo = mergeDefault(objectMapper.readValue(value, MerchantInfoDTO.class));
                } catch (JsonProcessingException ex) {
                    log.warn("Stored merchant information is malformed, keeping the last valid value");
                }
            }
            return copyOf(fallbackMerchantInfo);
        });
        return copyOf(cached);
    }

    @Transactional
    public void setMerchantInfo(MerchantInfoDTO merchantInfo) {
        MerchantInfoDTO next = mergeDefault(merchantInfo);
        String value;
        try {
            value = objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize merchant info", ex);
        }
        runtimeSettingMapper.upsert(MERCHANT_INFO_KEY, value);
        fallbackMerchantInfo = next;
        cacheInvalidationCoordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.MERCHANT_INFO)
                .build());
    }

    private MerchantInfoDTO mergeDefault(MerchantInfoDTO source) {
        MerchantInfoDTO defaults = defaultMerchantInfo();
        if (source == null) {
            return defaults;
        }
        defaults.setName(firstNonBlank(source.getName(), defaults.getName()));
        defaults.setSlogan(firstNonBlank(source.getSlogan(), defaults.getSlogan()));
        defaults.setPhone(firstNonBlank(source.getPhone(), defaults.getPhone()));
        defaults.setAddress(firstNonBlank(source.getAddress(), defaults.getAddress()));
        defaults.setBusinessHours(firstNonBlank(source.getBusinessHours(), defaults.getBusinessHours()));
        defaults.setNotice(firstNonBlank(source.getNotice(), defaults.getNotice()));
        defaults.setCoverImage(firstNonBlank(source.getCoverImage(), defaults.getCoverImage()));
        return defaults;
    }

    private MerchantInfoDTO defaultMerchantInfo() {
        MerchantInfoDTO dto = new MerchantInfoDTO();
        dto.setName("城市生活探店馆");
        dto.setSlogan("发现身边值得体验的本地生活内容");
        dto.setPhone("029-88888888");
        dto.setAddress("西安市雁塔区科技路 88 号");
        dto.setBusinessHours("10:00-22:00");
        dto.setNotice("预约前请确认门店营业状态，部分项目需提前联系商家。");
        dto.setCoverImage("");
        return dto;
    }

    private MerchantInfoDTO copyOf(MerchantInfoDTO source) {
        MerchantInfoDTO dto = new MerchantInfoDTO();
        dto.setName(source.getName());
        dto.setSlogan(source.getSlogan());
        dto.setPhone(source.getPhone());
        dto.setAddress(source.getAddress());
        dto.setBusinessHours(source.getBusinessHours());
        dto.setNotice(source.getNotice());
        dto.setCoverImage(source.getCoverImage());
        return dto;
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
