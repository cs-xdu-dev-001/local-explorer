package com.localexplorer.health;

import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.vo.ExportJobStatsVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExportJobHealthIndicatorTest {

    @Test
    void reportsDegradedWithoutTakingApplicationDown() {
        ExportJobMapper mapper = mock(ExportJobMapper.class);
        when(mapper.stats(any())).thenReturn(ExportJobStatsVO.builder()
                .pending(4L).failed(1L).expiredLeases(2L).build());
        ExportJobHealthIndicator indicator = new ExportJobHealthIndicator();
        ReflectionTestUtils.setField(indicator, "exportJobMapper", mapper);
        ReflectionTestUtils.setField(indicator, "clock", Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getStatus().getCode()).isNotEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("pendingJobs", 4L)
                .containsEntry("failedJobs", 1L).containsEntry("expiredLeases", 2L);
    }
}
