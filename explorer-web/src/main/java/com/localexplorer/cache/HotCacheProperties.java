package com.localexplorer.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "explorer.hot-cache")
public class HotCacheProperties {
    private boolean enabled = true;
    private boolean redisEnabled = true;
    private int schemaVersion = 1;
    private long l1MaximumSize = 2000;
    private long l1TtlMillis = 20000;
    private long l2TtlMillis = 600000;
    private long nullTtlMillis = 30000;
    private long staleTtlMillis = 120000;
    private long ttlJitterMillis = 60000;
    private long lockLeaseMillis = 3000;
    private long lockWaitMillis = 500;
    private long lockPollMillis = 25;
    private long singleFlightWaitMillis = 3000;
    private long redisCircuitOpenMillis = 1000;
    private String keyPrefix = "lx:hot:v1";
    private String invalidationChannel = "lx:hot:v1:invalidate";
}
