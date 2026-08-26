package com.localexplorer.config;

import com.localexplorer.service.OrderExpirationPolicy;
import com.localexplorer.service.OutboxRetryPolicy;
import com.localexplorer.service.ExportRetryPolicy;
import com.localexplorer.storage.ExportFileStorage;
import com.localexplorer.storage.LocalExportFileStorage;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.nio.file.Paths;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ReliabilityConfiguration {

    @Bean
    public Clock reliabilityClock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public OrderExpirationPolicy orderExpirationPolicy(
            Clock reliabilityClock,
            @Value("${explorer.order.pending-timeout-minutes:30}") long timeoutMinutes) {
        return new OrderExpirationPolicy(reliabilityClock, Duration.ofMinutes(timeoutMinutes));
    }

    @Bean
    public OutboxRetryPolicy outboxRetryPolicy(
            @Value("${explorer.jobs.outbox-max-attempts:5}") int maxAttempts,
            @Value("${explorer.jobs.outbox-base-delay-seconds:10}") long baseDelaySeconds) {
        return new OutboxRetryPolicy(maxAttempts, Duration.ofSeconds(baseDelaySeconds));
    }

    @Bean
    public ExportRetryPolicy exportRetryPolicy(ExportJobProperties properties) {
        return new ExportRetryPolicy(properties.getMaxAttempts(), Duration.ofSeconds(properties.getBaseRetrySeconds()));
    }

    @Bean
    public ExportFileStorage exportFileStorage(ExportJobProperties properties) {
        return new LocalExportFileStorage(Paths.get(properties.getStorageRoot()));
    }

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
