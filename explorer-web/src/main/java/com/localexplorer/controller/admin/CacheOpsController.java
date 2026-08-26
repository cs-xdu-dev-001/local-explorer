package com.localexplorer.controller.admin;

import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.annotation.OperationLog;
import com.localexplorer.cache.CacheSnapshot;
import com.localexplorer.cache.CacheWarmupService;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.exception.BaseException;
import com.localexplorer.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/cache")
@RequireAdmin
@Api(tags = "缓存运维接口")
public class CacheOpsController {
    private final HotReadCacheService cache;
    private final CacheWarmupService warmupService;

    @Autowired
    public CacheOpsController(HotReadCacheService cache, CacheWarmupService warmupService) {
        this.cache = cache;
        this.warmupService = warmupService;
    }

    @GetMapping("/stats")
    @ApiOperation("查询两级缓存统计")
    public Result<Map<String, Object>> stats() {
        CacheSnapshot snapshot = cache.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cache", snapshot);
        result.put("warmupRunning", warmupService.isRunning());
        result.put("lastWarmupAt", warmupService.getLastCompletedAt());
        result.put("lastWarmupEntries", warmupService.getLastLoadedEntries());
        return Result.success(result);
    }

    @PostMapping("/invalidate/{domain}")
    @ApiOperation("按业务域失效缓存")
    @OperationLog("失效公共浏览缓存")
    public Result<String> invalidate(@PathVariable String domain) {
        if ("all".equalsIgnoreCase(domain)) {
            for (HotCacheDomain cacheDomain : HotCacheDomain.values()) {
                cache.invalidateAll(cacheDomain);
            }
            return Result.success();
        }
        cache.invalidateAll(parseDomain(domain));
        return Result.success();
    }

    @PostMapping("/warmup")
    @ApiOperation("异步预热首页缓存")
    @OperationLog("预热公共浏览缓存")
    public Result<String> warmup() {
        warmupService.warmupHomepage();
        return Result.success();
    }

    private HotCacheDomain parseDomain(String value) {
        for (HotCacheDomain domain : HotCacheDomain.values()) {
            if (domain.name().equalsIgnoreCase(value) || domain.getCode().equalsIgnoreCase(value)) {
                return domain;
            }
        }
        throw new BaseException("不支持的缓存域");
    }
}
