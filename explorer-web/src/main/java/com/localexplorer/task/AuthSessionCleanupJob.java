package com.localexplorer.task;

import com.localexplorer.metrics.AuthenticationMetrics;
import com.localexplorer.service.AuthSessionService;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "explorer.jobs", name = "enabled", havingValue = "true")
public class AuthSessionCleanupJob {
    private final AuthSessionService service;
    private final AuthenticationMetrics metrics;

    public AuthSessionCleanupJob(AuthSessionService service, AuthenticationMetrics metrics) {
        this.service = service;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${explorer.jobs.auth-session-cleanup-delay-ms:3600000}")
    @SchedulerLock(name = "authSessionCleanupJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void run() {
        long started = System.nanoTime();
        try {
            int changed = service.cleanup();
            metrics.cleanup("success", System.nanoTime() - started);
            log.info("认证会话清理完成 changed={} elapsedMs={}", changed,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        } catch (RuntimeException ex) {
            metrics.cleanup("error", System.nanoTime() - started);
            log.error("认证会话清理失败", ex);
        }
    }
}
