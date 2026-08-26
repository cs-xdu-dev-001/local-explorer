package com.localexplorer.health;

import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.vo.ExportJobStatsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ExportJobHealthIndicator implements HealthIndicator {

    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private Clock clock;

    @Override
    public Health health() {
        LocalDateTime now = LocalDateTime.now(clock);
        ExportJobStatsVO stats = exportJobMapper.stats(now);
        long failed = stats == null || stats.getFailed() == null ? 0 : stats.getFailed();
        long expiredLeases = stats == null || stats.getExpiredLeases() == null ? 0 : stats.getExpiredLeases();
        long pending = stats == null || stats.getPending() == null ? 0 : stats.getPending();
        Health.Builder builder = failed > 0 || expiredLeases > 0 ? Health.status("DEGRADED") : Health.up();
        return builder.withDetail("pendingJobs", pending)
                .withDetail("failedJobs", failed)
                .withDetail("expiredLeases", expiredLeases).build();
    }
}
