package com.localexplorer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "explorer.export")
public class ExportJobProperties {

    private String storageRoot = System.getProperty("java.io.tmpdir") + "/local-explorer-exports";
    private int batchSize = 500;
    private int scanBatchSize = 10;
    private int maxAttempts = 4;
    private long baseRetrySeconds = 10;
    private long leaseSeconds = 60;
    private long heartbeatSeconds = 20;
    private int maxActivePerOperator = 3;
    private int maxRangeDays = 366;
    private long maxRows = 100000;
    private long maxFileBytes = 52428800;
    private long maxRuntimeSeconds = 900;
    private long fileTtlHours = 24;
    private long scanDelayMs = 3000;
    private long cleanupDelayMs = 60000;
    private int workerThreads = 2;
    private int workerQueueCapacity = 20;
    private String snapshotSecret = "local-explorer-export-snapshot-dev-secret";
}
