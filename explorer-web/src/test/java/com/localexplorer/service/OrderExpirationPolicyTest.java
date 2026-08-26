package com.localexplorer.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class OrderExpirationPolicyTest {

    @Test
    void deadlineUsesInjectedClockAndConfiguredTimeout() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneId.of("Asia/Shanghai"));
        OrderExpirationPolicy policy = new OrderExpirationPolicy(clock, Duration.ofMinutes(30));

        assertThat(policy.newDeadline())
                .isEqualTo(LocalDateTime.of(2026, 8, 24, 8, 30));
        assertThat(policy.isExpired(LocalDateTime.of(2026, 8, 24, 8, 30),
                LocalDateTime.of(2026, 8, 24, 8, 30))).isTrue();
        assertThat(policy.isExpired(LocalDateTime.of(2026, 8, 24, 8, 31),
                LocalDateTime.of(2026, 8, 24, 8, 30))).isFalse();
    }
}
