package com.localexplorer.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CacheInvalidationCoordinatorTest {

    @AfterEach
    void cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void invalidatesImmediatelyWithoutTransaction() {
        HotReadCacheService cache = mock(HotReadCacheService.class);
        CacheInvalidationCoordinator coordinator = new CacheInvalidationCoordinator(cache);

        coordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.ITEM_LIST)
                .evict(HotCacheDomain.ITEM_DETAIL, "1001")
                .build());

        verify(cache).invalidateAll(HotCacheDomain.ITEM_LIST);
        verify(cache).invalidate(HotCacheDomain.ITEM_DETAIL, "1001");
    }

    @Test
    void commitsBeforeInvalidating() {
        HotReadCacheService cache = mock(HotReadCacheService.class);
        CacheInvalidationCoordinator coordinator = new CacheInvalidationCoordinator(cache);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        coordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.PACKAGE_LIST)
                .build());

        verify(cache, never()).invalidateAll(HotCacheDomain.PACKAGE_LIST);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(cache).invalidateAll(HotCacheDomain.PACKAGE_LIST);
    }

    @Test
    void rollbackDoesNotInvalidate() {
        HotReadCacheService cache = mock(HotReadCacheService.class);
        CacheInvalidationCoordinator coordinator = new CacheInvalidationCoordinator(cache);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        coordinator.invalidate(CacheInvalidation.builder()
                .clear(HotCacheDomain.CATEGORY_LIST)
                .build());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(cache, never()).invalidateAll(HotCacheDomain.CATEGORY_LIST);
    }
}
