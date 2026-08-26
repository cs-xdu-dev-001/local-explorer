package com.localexplorer.cache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class HotCacheTestDoubles {
    private HotCacheTestDoubles() {
    }

    public static HotReadCacheService passThroughCache() {
        HotReadCacheService cache = mock(HotReadCacheService.class);
        lenient().when(cache.get(any(HotCacheDomain.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    HotReadCacheService.CacheLoader<?> loader = invocation.getArgument(2);
                    return loader.load();
                });
        return cache;
    }
}
