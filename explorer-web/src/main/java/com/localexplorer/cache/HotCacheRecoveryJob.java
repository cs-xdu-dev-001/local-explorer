package com.localexplorer.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "explorer.hot-cache", name = "recovery-job-enabled",
        havingValue = "true", matchIfMissing = true)
public class HotCacheRecoveryJob {
    private final HotReadCacheService cache;

    @Autowired
    public HotCacheRecoveryJob(HotReadCacheService cache) {
        this.cache = cache;
    }

    @Scheduled(fixedDelayString = "${explorer.hot-cache.recovery-delay-millis:2000}")
    public void flushPendingInvalidations() {
        cache.flushPendingInvalidations();
    }
}
