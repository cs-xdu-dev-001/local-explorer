package com.localexplorer.integration;

import com.localexplorer.LocalExplorerApplication;
import com.localexplorer.cache.CacheInvalidation;
import com.localexplorer.cache.CacheInvalidationCoordinator;
import com.localexplorer.cache.HotCacheDomain;
import com.localexplorer.cache.HotCacheRedisClient;
import com.localexplorer.cache.HotReadCacheService;
import com.localexplorer.service.RuntimeSettingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HotCacheMySqlRedisIT {
    private static final String PREFIX = "lx:hot:it:" + UUID.randomUUID();
    private static final String CHANNEL = PREFIX + ":invalidate";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("local_explorer")
            .withUsername("root")
            .withPassword("test-password")
            .withInitScript("local-explorer-init.sql");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    private static ConfigurableApplicationContext firstContext;
    private static ConfigurableApplicationContext secondContext;

    @BeforeAll
    static void startApplications() {
        System.setProperty("druid.mysql.usePingMethod", "false");
        firstContext = startApplication();
        secondContext = startApplication();
    }

    @AfterAll
    static void stopApplications() {
        if (secondContext != null) {
            secondContext.close();
        }
        if (firstContext != null) {
            firstContext.close();
        }
    }

    @Test
    @Order(1)
    void realRedisL2HandlesCorruptPayloadAndReloadsFromMySql() throws Exception {
        HotReadCacheService first = firstContext.getBean(HotReadCacheService.class);
        HotReadCacheService second = secondContext.getBean(HotReadCacheService.class);
        HotCacheRedisClient redis = firstContext.getBean(HotCacheRedisClient.class);
        StringRedisTemplate template = firstContext.getBean(StringRedisTemplate.class);
        JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
        AtomicInteger databaseLoads = new AtomicInteger();
        String businessKey = "real-l2-" + UUID.randomUUID();

        assertThat(first.get(HotCacheDomain.SHOP_STATUS, businessKey, () -> {
            databaseLoads.incrementAndGet();
            return jdbc.queryForObject("select 41", Integer.class);
        })).isEqualTo(41);
        second.clearLocal();
        assertThat(second.get(HotCacheDomain.SHOP_STATUS, businessKey, () -> {
            databaseLoads.incrementAndGet();
            return 0;
        })).isEqualTo(41);
        assertThat(databaseLoads).hasValue(1);

        long namespace = redis.resolveNamespace(HotCacheDomain.SHOP_STATUS.getCode(), 1);
        template.opsForValue().set(dataKey(HotCacheDomain.SHOP_STATUS, namespace, businessKey),
                "{malformed-json", Duration.ofMinutes(1));
        first.clearLocal();
        second.clearLocal();

        assertThat(second.get(HotCacheDomain.SHOP_STATUS, businessKey, () -> {
            databaseLoads.incrementAndGet();
            return jdbc.queryForObject("select 42", Integer.class);
        })).isEqualTo(42);
        assertThat(databaseLoads).hasValue(2);
        assertThat(second.snapshot().getCorruptedEntries()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(2)
    void twoSpringApplicationsShareOneDistributedLoadWithLeaseRenewal() throws Exception {
        HotReadCacheService first = firstContext.getBean(HotReadCacheService.class);
        HotReadCacheService second = secondContext.getBean(HotReadCacheService.class);
        JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
        AtomicInteger databaseLoads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        String businessKey = "distributed-" + UUID.randomUUID();
        try {
            Future<Integer> firstResult = executor.submit(() -> {
                start.await();
                return first.get(HotCacheDomain.SHOP_STATUS, businessKey, () -> slowMySql(jdbc, databaseLoads));
            });
            Future<Integer> secondResult = executor.submit(() -> {
                start.await();
                return second.get(HotCacheDomain.SHOP_STATUS, businessKey, () -> slowMySql(jdbc, databaseLoads));
            });
            start.countDown();

            assertThat(firstResult.get(10, TimeUnit.SECONDS)).isEqualTo(43);
            assertThat(secondResult.get(10, TimeUnit.SECONDS)).isEqualTo(43);
            assertThat(databaseLoads).hasValue(1);
            assertThat(first).isNotSameAs(second);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Order(3)
    void committedWriteInvalidatesOtherApplicationsL1AndRollbackDoesNot() {
        RuntimeSettingService firstSettings = firstContext.getBean(RuntimeSettingService.class);
        RuntimeSettingService secondSettings = secondContext.getBean(RuntimeSettingService.class);
        HotReadCacheService firstCache = firstContext.getBean(HotReadCacheService.class);
        HotReadCacheService secondCache = secondContext.getBean(HotReadCacheService.class);
        JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
        CacheInvalidationCoordinator coordinator = firstContext.getBean(CacheInvalidationCoordinator.class);
        TransactionTemplate transaction = firstContext.getBean(TransactionTemplate.class);

        firstSettings.setShopStatus(1);
        assertThat(firstSettings.getShopStatus()).isEqualTo(1);
        assertThat(secondSettings.getShopStatus()).isEqualTo(1);

        firstSettings.setShopStatus(0);
        await(() -> secondSettings.getShopStatus() == 0, 5000);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("update runtime_setting set setting_value='1' where setting_key='SHOP_STATUS'");
            coordinator.invalidate(CacheInvalidation.builder().clear(HotCacheDomain.SHOP_STATUS).build());
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject(
                "select setting_value from runtime_setting where setting_key='SHOP_STATUS'", String.class))
                .isEqualTo("0");
        assertThat(firstCache.snapshot().getPendingInvalidations()).isZero();
        assertThat(secondCache.snapshot().getPendingInvalidations()).isZero();
        assertThat(secondSettings.getShopStatus()).isEqualTo(0);
    }

    @Test
    @Order(4)
    void redisTimeoutFallsBackQuicklyAndRecoveryRepopulatesL2() throws Exception {
        HotReadCacheService first = firstContext.getBean(HotReadCacheService.class);
        HotReadCacheService second = secondContext.getBean(HotReadCacheService.class);
        JdbcTemplate jdbc = firstContext.getBean(JdbcTemplate.class);
        String cachedKey = "available-before-outage-" + UUID.randomUUID();
        String outageKey = "outage-" + UUID.randomUUID();

        assertThat(first.get(HotCacheDomain.SHOP_STATUS, cachedKey,
                () -> jdbc.queryForObject("select 44", Integer.class))).isEqualTo(44);
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            assertThat(first.get(HotCacheDomain.SHOP_STATUS, cachedKey, () -> 0)).isEqualTo(44);
            assertThat(first.get(HotCacheDomain.SHOP_STATUS, outageKey,
                    () -> jdbc.queryForObject("select 45", Integer.class))).isEqualTo(45);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
            assertThat(first.snapshot().getRedisDegradations()).isGreaterThanOrEqualTo(1);
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }

        awaitRedis(firstContext.getBean(StringRedisTemplate.class), 5000);
        Thread.sleep(1200);
        String recoveredKey = "recovered-" + UUID.randomUUID();
        assertThat(first.get(HotCacheDomain.SHOP_STATUS, recoveredKey,
                () -> jdbc.queryForObject("select 46", Integer.class))).isEqualTo(46);
        second.clearLocal();
        assertThat(second.<Integer>get(HotCacheDomain.SHOP_STATUS, recoveredKey,
                () -> { throw new AssertionError("expected recovered Redis L2 hit"); })).isEqualTo(46);
        assertThat(first.snapshot().isRedisCircuitOpen()).isFalse();
    }

    private static ConfigurableApplicationContext startApplication() {
        return new SpringApplicationBuilder(LocalExplorerApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.jmx.enabled=false",
                        "--explorer.datasource.host=" + MYSQL.getHost(),
                        "--explorer.datasource.port=" + MYSQL.getMappedPort(3306),
                        "--explorer.datasource.database=" + MYSQL.getDatabaseName(),
                        "--explorer.datasource.username=" + MYSQL.getUsername(),
                        "--explorer.datasource.password=" + MYSQL.getPassword(),
                        "--explorer.redis.host=" + REDIS.getHost(),
                        "--explorer.redis.port=" + REDIS.getMappedPort(6379),
                        "--explorer.redis.database=0",
                        "--explorer.hot-cache.redis-enabled=true",
                        "--explorer.hot-cache.recovery-job-enabled=false",
                        "--explorer.hot-cache.key-prefix=" + PREFIX,
                        "--explorer.hot-cache.invalidation-channel=" + CHANNEL,
                        "--explorer.hot-cache.l1-ttl-millis=5000",
                        "--explorer.hot-cache.l2-ttl-millis=30000",
                        "--explorer.hot-cache.ttl-jitter-millis=0",
                        "--explorer.hot-cache.lock-lease-millis=150",
                        "--explorer.hot-cache.lock-wait-millis=2000",
                        "--explorer.hot-cache.redis-circuit-open-millis=1000",
                        "--explorer.jobs.enabled=false"
                );
    }

    private static int slowMySql(JdbcTemplate jdbc, AtomicInteger databaseLoads) throws Exception {
        databaseLoads.incrementAndGet();
        Thread.sleep(450);
        return jdbc.queryForObject("select 43", Integer.class);
    }

    private static String dataKey(HotCacheDomain domain, long namespace, String businessKey) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(businessKey.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash = new StringBuilder();
        for (int index = 0; index < 12; index++) {
            hash.append(String.format("%02x", digest[index]));
        }
        return PREFIX + ":data:" + domain.getCode() + ":s1:n" + namespace + ":k" + hash;
    }

    private static void await(Check check, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            try {
                if (check.isSatisfied()) {
                    return;
                }
                Thread.sleep(50);
            } catch (Exception ignored) {
                // Retry until the bounded deadline.
            }
        }
        throw new AssertionError("condition was not satisfied before timeout");
    }

    private static void awaitRedis(StringRedisTemplate template, long timeoutMillis) {
        await(() -> "PONG".equalsIgnoreCase(template.getConnectionFactory().getConnection().ping()), timeoutMillis);
    }

    @FunctionalInterface
    private interface Check {
        boolean isSatisfied() throws Exception;
    }
}
