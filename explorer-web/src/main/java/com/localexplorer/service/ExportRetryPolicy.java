package com.localexplorer.service;

import java.time.Duration;
import java.time.LocalDateTime;

public class ExportRetryPolicy {

    private static final int MAX_ERROR_LENGTH = 200;
    private final int maxAttempts;
    private final Duration baseDelay;

    public ExportRetryPolicy(int maxAttempts, Duration baseDelay) {
        if (maxAttempts < 1 || baseDelay == null || baseDelay.isZero() || baseDelay.isNegative()) {
            throw new IllegalArgumentException("Invalid export retry policy");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
    }

    public LocalDateTime nextRetryAt(LocalDateTime now, int retryCount) {
        long multiplier = 1L << Math.max(0, Math.min(retryCount - 1, 10));
        return now.plus(baseDelay.multipliedBy(multiplier));
    }

    public boolean shouldFailPermanently(int retryCount) {
        return retryCount >= maxAttempts;
    }

    public String sanitizeError(String error) {
        String value = error == null ? "unknown" : error.replaceAll("[\\r\\n\\t]", " ");
        value = value.replaceAll("(?i)(token|password|secret)\\s*[=:]\\s*[^ ]+", "$1=***");
        value = value.replaceAll("(?<!\\d)1\\d{10}(?!\\d)", "1**********");
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
