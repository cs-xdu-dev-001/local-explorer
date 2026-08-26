package com.localexplorer.domain;

public final class OrderEventType {

    public static final String CONFIRMED = "ORDER_CONFIRMED";
    public static final String COMPLETED = "ORDER_COMPLETED";
    public static final String CANCELED_BY_USER = "ORDER_CANCELED_BY_USER";
    public static final String CANCELED_BY_ADMIN = "ORDER_CANCELED_BY_ADMIN";
    public static final String EXPIRED = "ORDER_EXPIRED";

    private OrderEventType() {
    }
}
