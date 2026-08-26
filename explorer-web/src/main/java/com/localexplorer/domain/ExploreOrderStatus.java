package com.localexplorer.domain;

import java.util.Arrays;

public enum ExploreOrderStatus {

    PENDING(0),
    CONFIRMED(1),
    COMPLETED(2),
    CANCELED(3),
    EXPIRED(4);

    private final int code;

    ExploreOrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public boolean canTransitionTo(ExploreOrderStatus target) {
        if (target == this) {
            return true;
        }
        if (this == PENDING) {
            return target == CONFIRMED || target == CANCELED || target == EXPIRED;
        }
        if (this == CONFIRMED) {
            return target == COMPLETED || target == CANCELED;
        }
        return false;
    }

    public static ExploreOrderStatus fromCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> Integer.valueOf(status.code).equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order status: " + code));
    }
}
