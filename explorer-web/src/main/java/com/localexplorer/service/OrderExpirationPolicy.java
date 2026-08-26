package com.localexplorer.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

public class OrderExpirationPolicy {

    private final Clock clock;
    private final Duration pendingTimeout;

    public OrderExpirationPolicy(Clock clock, Duration pendingTimeout) {
        if (pendingTimeout == null || pendingTimeout.isZero() || pendingTimeout.isNegative()) {
            throw new IllegalArgumentException("pendingTimeout must be positive");
        }
        this.clock = clock;
        this.pendingTimeout = pendingTimeout;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDateTime newDeadline() {
        return now().plus(pendingTimeout);
    }

    public boolean isExpired(LocalDateTime expireAt, LocalDateTime now) {
        return expireAt != null && !expireAt.isAfter(now);
    }
}
