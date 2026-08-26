package com.localexplorer.cache;

import com.localexplorer.constant.StatusConstant;
import com.localexplorer.entity.Category;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.service.CategoryService;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.service.ExplorePackageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class CacheWarmupService {
    private final CategoryService categoryService;
    private final ExploreItemService itemService;
    private final ExplorePackageService packageService;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong lastCompletedAt = new AtomicLong();
    private final AtomicLong lastLoadedEntries = new AtomicLong();

    @Autowired
    public CacheWarmupService(CategoryService categoryService, ExploreItemService itemService,
                              ExplorePackageService packageService) {
        this.categoryService = categoryService;
        this.itemService = itemService;
        this.packageService = packageService;
    }

    @Async("cacheWarmupExecutor")
    public void warmupHomepage() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long loaded = 0;
        try {
            List<Category> itemCategories = categoryService.list(1);
            List<Category> packageCategories = categoryService.list(2);
            loaded += itemCategories.size() + packageCategories.size();
            for (Category category : itemCategories) {
                ExploreItem query = ExploreItem.builder()
                        .categoryId(category.getId())
                        .status(StatusConstant.ENABLE)
                        .build();
                loaded += itemService.listWithTags(query).size();
            }
            for (Category category : packageCategories) {
                ExplorePackage query = ExplorePackage.builder()
                        .categoryId(category.getId())
                        .status(StatusConstant.ENABLE)
                        .build();
                loaded += packageService.list(query).size();
            }
            lastLoadedEntries.set(loaded);
            lastCompletedAt.set(System.currentTimeMillis());
        } catch (RuntimeException ex) {
            log.warn("Cache warmup finished with an error: {}", ex.getClass().getSimpleName());
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getLastCompletedAt() {
        return lastCompletedAt.get();
    }

    public long getLastLoadedEntries() {
        return lastLoadedEntries.get();
    }
}
