package com.localexplorer.health;

import com.localexplorer.mapper.OrderEventOutboxMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorTest {

    private OutboxHealthIndicator indicator;
    @Mock private OrderEventOutboxMapper outboxMapper;

    @BeforeEach
    void setUp() {
        indicator = new OutboxHealthIndicator();
        ReflectionTestUtils.setField(indicator, "outboxMapper", outboxMapper);
    }

    @Test
    void deadBacklogIsDegradedWithoutReportingServiceDown() {
        when(outboxMapper.countDead()).thenReturn(2L);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getStatus().getCode()).isNotEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("deadEvents", 2L);
    }

    @Test
    void emptyDeadBacklogIsHealthy() {
        when(outboxMapper.countDead()).thenReturn(0L);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
    }
}
