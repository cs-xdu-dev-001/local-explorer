package com.localexplorer.service.impl;

import com.localexplorer.metrics.OrderReliabilityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class OutboxEventProcessor {

    @Autowired private OutboxEventTransactionService transactionService;
    @Autowired private OrderReliabilityMetrics metrics;

    public boolean process(Long eventId, LocalDateTime now, int lockSeconds) {
        String lockToken = UUID.randomUUID().toString();
        if (!transactionService.claim(eventId, lockToken, now, now.plusSeconds(lockSeconds))) {
            metrics.recordOutboxResult("claim_conflict");
            return false;
        }
        try {
            transactionService.deliver(eventId, lockToken, now);
            metrics.recordOutboxResult("processed");
            return true;
        } catch (RuntimeException ex) {
            try {
                boolean dead = transactionService.recordFailure(eventId, lockToken, ex, now);
                metrics.recordOutboxResult(dead ? "dead" : "retry");
            } catch (RuntimeException recordFailure) {
                metrics.recordOutboxResult("failure_record_error");
                log.error("Outbox event {} failed and retry state could not be recorded", eventId, recordFailure);
            }
            log.warn("Outbox event {} delivery failed: {}", eventId, ex.getClass().getSimpleName());
            return false;
        }
    }
}
