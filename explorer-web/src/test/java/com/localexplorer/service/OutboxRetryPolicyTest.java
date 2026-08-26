package com.localexplorer.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryPolicyTest {

    private final OutboxRetryPolicy policy = new OutboxRetryPolicy(4, Duration.ofSeconds(10));

    @Test
    void retryDelayUsesBoundedExponentialBackoff() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 8, 0);

        assertThat(policy.nextRetryAt(now, 1)).isEqualTo(now.plusSeconds(10));
        assertThat(policy.nextRetryAt(now, 2)).isEqualTo(now.plusSeconds(20));
        assertThat(policy.nextRetryAt(now, 3)).isEqualTo(now.plusSeconds(40));
    }

    @Test
    void reachingMaxAttemptsMovesEventToDead() {
        assertThat(policy.shouldMarkDead(3)).isFalse();
        assertThat(policy.shouldMarkDead(4)).isTrue();
        assertThat(policy.sanitizeError("token=secret\nphone=13800001111"))
                .doesNotContain("secret", "13800001111", "\n")
                .hasSizeLessThanOrEqualTo(200);
    }
}
