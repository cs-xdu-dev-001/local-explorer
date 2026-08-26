package com.localexplorer.task;

import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.metrics.OrderReliabilityMetrics;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.impl.OutboxEventProcessor;
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
public class OutboxDispatchJob {

    @Autowired private OrderEventOutboxMapper outboxMapper;
    @Autowired private OutboxEventProcessor processor;
    @Autowired private OrderEventOutboxService outboxService;
    @Autowired private OrderExpirationPolicy expirationPolicy;
    @Autowired private OrderReliabilityMetrics metrics;
    @Value("${explorer.jobs.batch-size:50}") private int batchSize;
    @Value("${explorer.jobs.outbox-lock-seconds:60}") private int lockSeconds;

    @Scheduled(fixedDelayString = "${explorer.jobs.outbox-delay-ms:5000}")
    @SchedulerLock(name = "outboxDispatchJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        long started = System.nanoTime();
        String batchId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("batchId", batchId);
        try {
            LocalDateTime now = expirationPolicy.now();
            List<Long> ids = outboxMapper.findReadyIds(now, batchSize);
            for (Long id : ids) {
                long itemStarted = System.nanoTime();
                try {
                    boolean processed = processor.process(id, now, lockSeconds);
                    log.info("Outbox item batchId={} eventRowId={} result={} elapsedMs={}",
                            batchId, id, processed ? "processed" : "not_processed",
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - itemStarted));
                } catch (RuntimeException ex) {
                    metrics.recordOutboxResult("batch_item_failed");
                    log.error("Outbox item batchId={} eventRowId={} result=failed elapsedMs={}",
                            batchId, id,
                            java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - itemStarted), ex);
                }
            }
            metrics.updateBacklog(outboxService.stats());
            log.info("Outbox batch batchId={} scanned={} result=completed", batchId, ids.size());
        } catch (RuntimeException ex) {
            metrics.recordOutboxResult("scan_failed");
            log.error("Outbox batch batchId={} result=scan_failed", batchId, ex);
        } finally {
            metrics.recordOutboxBatch(System.nanoTime() - started);
            MDC.remove("batchId");
        }
    }
}
