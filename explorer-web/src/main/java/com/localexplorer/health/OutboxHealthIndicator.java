package com.localexplorer.health;

import com.localexplorer.mapper.OrderEventOutboxMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class OutboxHealthIndicator implements HealthIndicator {

    @Autowired private OrderEventOutboxMapper outboxMapper;

    @Override
    public Health health() {
        long dead = outboxMapper.countDead();
        if (dead > 0) {
            return Health.status("DEGRADED").withDetail("deadEvents", dead).build();
        }
        return Health.up().withDetail("deadEvents", 0).build();
    }
}
