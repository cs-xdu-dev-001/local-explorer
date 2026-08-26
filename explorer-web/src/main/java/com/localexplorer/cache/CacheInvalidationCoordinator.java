package com.localexplorer.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CacheInvalidationCoordinator {
    private final HotReadCacheService cache;

    @Autowired
    public CacheInvalidationCoordinator(HotReadCacheService cache) {
        this.cache = cache;
    }

    public void invalidate(CacheInvalidation invalidation) {
        Runnable action = () -> execute(invalidation);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private void execute(CacheInvalidation invalidation) {
        invalidation.getClearedDomains().forEach(cache::invalidateAll);
        invalidation.getKeys().forEach(key -> cache.invalidate(key.getDomain(), key.getBusinessKey()));
    }
}
