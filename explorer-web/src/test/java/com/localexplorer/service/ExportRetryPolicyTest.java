package com.localexplorer.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportRetryPolicyTest {

    @Test
    void usesBoundedExponentialBackoff() {
        ExportRetryPolicy policy = new ExportRetryPolicy(4, Duration.ofSeconds(5));
        LocalDateTime now = LocalDateTime.of(2026, 8, 24, 12, 0);

        assertThat(policy.nextRetryAt(now, 1)).isEqualTo(now.plusSeconds(5));
        assertThat(policy.nextRetryAt(now, 2)).isEqualTo(now.plusSeconds(10));
        assertThat(policy.nextRetryAt(now, 3)).isEqualTo(now.plusSeconds(20));
        assertThat(policy.shouldFailPermanently(3)).isFalse();
        assertThat(policy.shouldFailPermanently(4)).isTrue();
    }

    @Test
    void sanitizesSensitiveAndOversizedErrors() {
        ExportRetryPolicy policy = new ExportRetryPolicy(3, Duration.ofSeconds(1));
        String error = "token=abc password:secret 13800001111\r\n" + repeat("x", 300);

        String sanitized = policy.sanitizeError(error);

        assertThat(sanitized)
                .doesNotContain("abc", "secret", "13800001111", "\r", "\n")
                .hasSizeLessThanOrEqualTo(200);
    }

    @Test
    void rejectsUnboundedConfiguration() {
        assertThatThrownBy(() -> new ExportRetryPolicy(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExportRetryPolicy(3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
