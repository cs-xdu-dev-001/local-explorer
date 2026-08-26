package com.localexplorer.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.vo.ExploreItemVO;
import com.localexplorer.vo.ExplorePackageVO;
import com.localexplorer.vo.PackageItemVO;

import java.util.List;

public enum HotCacheDomain {
    CATEGORY_LIST("category-list"),
    ITEM_LIST("item-list"),
    ITEM_DETAIL("item-detail"),
    PACKAGE_LIST("package-list"),
    PACKAGE_DETAIL("package-detail"),
    PACKAGE_ITEMS("package-items"),
    MERCHANT_INFO("merchant-info"),
    SHOP_STATUS("shop-status");

    private final String code;

    HotCacheDomain(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public JavaType javaType(ObjectMapper objectMapper) {
        switch (this) {
            case CATEGORY_LIST:
                return listType(objectMapper, Category.class);
            case ITEM_LIST:
                return listType(objectMapper, ExploreItemVO.class);
            case ITEM_DETAIL:
                return objectMapper.constructType(ExploreItemVO.class);
            case PACKAGE_LIST:
                return listType(objectMapper, ExplorePackage.class);
            case PACKAGE_DETAIL:
                return objectMapper.constructType(ExplorePackageVO.class);
            case PACKAGE_ITEMS:
                return listType(objectMapper, PackageItemVO.class);
            case MERCHANT_INFO:
                return objectMapper.constructType(MerchantInfoDTO.class);
            case SHOP_STATUS:
                return objectMapper.constructType(Integer.class);
            default:
                throw new IllegalStateException("Unsupported cache domain: " + this);
        }
    }

    private JavaType listType(ObjectMapper objectMapper, Class<?> elementType) {
        return objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
    }
}
