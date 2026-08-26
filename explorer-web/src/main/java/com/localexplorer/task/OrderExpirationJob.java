package com.localexplorer.task;

import com.localexplorer.domain.ExploreOrderStatus;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.metrics.OrderReliabilityMetrics;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.impl.ExpiredOrderProcessor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "explorer.jobs", name = "enabled", havingValue = "true")
@Slf4j
public class OrderExpirationJob {

    @Autowired private ExploreOrderMapper orderMapper;
    @Autowired private ExpiredOrderProcessor processor;
    @Autowired private OrderExpirationPolicy expirationPolicy;
    @Autowired private OrderReliabilityMetrics metrics;
    @Value("${explorer.jobs.batch-size:50}") private int batchSize;

    @Scheduled(fixedDelayString = "${explorer.jobs.expiration-delay-ms:30000}")
    @SchedulerLock(name = "orderExpirationJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        long started = System.nanoTime();
        String batchId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("batchId", batchId);
        try {
            LocalDateTime now = expirationPolicy.now();
            List<Long> ids = orderMapper.findExpiredIds(
                    ExploreOrderStatus.PENDING.getCode(), now, batchSize);
            metrics.recordExpirationScanned(ids.size());
            for (Long id : ids) {
                long itemStarted = System.nanoTime();
                try {
                    String result = processor.expire(id, now) ? "expired" : "cas_conflict";
                    metrics.recordExpirationResult(result);
                    log.info("Expiration item batchId={} orderId={} result={} elapsedMs={}",
                            batchId, id, result,
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - itemStarted));
                } catch (RuntimeException ex) {
                    metrics.recordExpirationResult("failed");
                    log.error("Expiration item batchId={} orderId={} result=failed elapsedMs={}",
                            batchId, id,
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - itemStarted), ex);
                }
            }
            log.info("Expiration batch batchId={} scanned={} result=completed", batchId, ids.size());
        } catch (RuntimeException ex) {
            metrics.recordExpirationResult("scan_failed");
            log.error("Expiration batch batchId={} result=scan_failed", batchId, ex);
        } finally {
            metrics.recordExpirationBatch(System.nanoTime() - started);
            MDC.remove("batchId");
        }
    }
}
