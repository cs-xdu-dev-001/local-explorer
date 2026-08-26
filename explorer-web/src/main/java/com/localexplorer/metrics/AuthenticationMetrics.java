package com.localexplorer.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class AuthenticationMetrics {
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("EMPLOYEE", "USER"));
    private static final Set<String> RESULTS = new HashSet<>(Arrays.asList(
            "success", "invalid", "locked", "expired", "revoked", "replay", "cas_conflict", "error"));
    private final MeterRegistry registry;

    public AuthenticationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void login(String type, String result) { counter("login", type, result); }
    public void refresh(String type, String result) { counter("refresh", type, result); }
    public void revoked(String type, int count) {
        registry.counter("local.explorer.auth.revoked", "principal_type", safeType(type)).increment(Math.max(count, 0));
    }
    public void cleanup(String result, long nanos) {
        Timer.builder("local.explorer.auth.cleanup").tag("result", safeResult(result))
                .register(registry).record(nanos, TimeUnit.NANOSECONDS);
    }
    public void latency(String operation, String type, long nanos) {
        String safeOperation = "refresh".equals(operation) ? "refresh" : "login";
        Timer.builder("local.explorer.auth." + safeOperation + ".latency")
                .tag("principal_type", safeType(type)).register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
    private void counter(String operation, String type, String result) {
        registry.counter("local.explorer.auth." + operation,
                "principal_type", safeType(type), "result", safeResult(result)).increment();
    }
    private String safeType(String type) { return TYPES.contains(type) ? type : "UNKNOWN"; }
    private String safeResult(String result) { return RESULTS.contains(result) ? result : "error"; }
}
