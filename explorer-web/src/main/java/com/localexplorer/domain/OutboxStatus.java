package com.localexplorer.domain;

public final class OutboxStatus {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String PROCESSED = "PROCESSED";
    public static final String DEAD = "DEAD";

    private OutboxStatus() {
    }
}
