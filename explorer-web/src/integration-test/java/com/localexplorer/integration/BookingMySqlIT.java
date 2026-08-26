package com.localexplorer.integration;

import com.localexplorer.dto.ExploreOrderDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.ExploreOrderMapper;
import com.localexplorer.mapper.OrderEventOutboxMapper;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.service.RuntimeSettingService;
import com.localexplorer.service.impl.ExpiredOrderProcessor;
import com.localexplorer.service.impl.OutboxEventProcessor;
import com.localexplorer.service.impl.OutboxEventTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@ActiveProfiles("test")
@Testcontainers
class BookingMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("local_explorer")
            .withUsername("root")
            .withPassword("test-password")
            .withInitScript("local-explorer-init.sql");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("explorer.datasource.host", MYSQL::getHost);
        registry.add("explorer.datasource.port", () -> MYSQL.getMappedPort(3306));
        registry.add("explorer.datasource.database", MYSQL::getDatabaseName);
        registry.add("explorer.datasource.username", MYSQL::getUsername);
        registry.add("explorer.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ExploreOrderMapper orderMapper;
    @Autowired
    private ExploreOrderService orderService;
    @Autowired
    private RuntimeSettingService runtimeSettingService;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ExpiredOrderProcessor expiredOrderProcessor;
    @Autowired
    private OutboxEventProcessor outboxEventProcessor;
    @Autowired
    private OutboxEventTransactionService outboxTransactionService;
    @Autowired
    private OrderEventOutboxMapper outboxMapper;

    @BeforeEach
    void resetBookingFixture() {
        jdbcTemplate.update("delete from user_notification where order_id in " +
                "(select id from explore_order where request_id like 'it-%')");
        jdbcTemplate.update("delete from order_event_outbox where aggregate_id in " +
                "(select id from explore_order where request_id like 'it-%')");
        jdbcTemplate.update("delete from explore_order where request_id like 'it-%'");
        jdbcTemplate.update("update explore_item set capacity = 10, booked = 0, status = 1 where id = 1001");
        runtimeSettingService.setShopStatus(1);
    }

    @Test
    void initializationScriptCreatesSchemaIndexesForeignKeysAndMapperMappings() {
        assertThat(countMetadata("information_schema.columns",
                "table_schema = database() and table_name = 'explore_order' and column_name = 'request_id'"))
                .isEqualTo(1);
        assertThat(countMetadata("information_schema.statistics",
                "table_schema = database() and table_name = 'explore_order' and index_name = 'idx_order_user_request'"))
                .isGreaterThanOrEqualTo(1);
        assertThat(countMetadata("information_schema.referential_constraints",
                "constraint_schema = database() and table_name = 'explore_order' and constraint_name = 'fk_order_user'"))
                .isEqualTo(1);
        assertThat(countMetadata("information_schema.columns",
                "table_schema = database() and table_name = 'explore_order' " +
                        "and column_name in ('expire_at','cancel_type','cancel_reason')"))
                .isEqualTo(3);
        assertThat(countMetadata("information_schema.tables",
                "table_schema = database() and table_name in " +
                        "('order_event_outbox','user_notification','shedlock')"))
                .isEqualTo(3);
        assertThat(countMetadata("information_schema.statistics",
                "table_schema = database() and table_name = 'explore_order' " +
                        "and index_name = 'idx_order_status_expire'"))
                .isEqualTo(2);

        Map<String, Object> plan = jdbcTemplate.queryForMap(
                "explain select id from explore_order where status = 0 " +
                        "and expire_at <= now() order by expire_at, id limit 50");
        assertThat(plan.get("key")).isEqualTo("idx_order_status_expire");

        ExploreOrder seededOrder = orderMapper.getById(3001L);
        assertThat(seededOrder).isNotNull();
        assertThat(seededOrder.getOrderNo()).isEqualTo("ORD20260425001");
        assertThat(seededOrder.getUserId()).isEqualTo(1L);
    }

    @Test
    void idempotentBookingReservesOnceAndCancellationRestoresCapacity() {
        ExploreOrderDTO dto = itemOrder("it-idempotent", 2);

        Long firstId = orderService.create(dto, 1L);
        Long secondId = orderService.create(dto, 1L);

        assertThat(secondId).isEqualTo(firstId);
        assertThat(orderMapper.getById(firstId).getExpireAt()).isNotNull();
        assertThat(bookedCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from explore_order where user_id = 1 and request_id = 'it-idempotent'",
                Integer.class)).isEqualTo(1);

        orderService.cancelByUser(firstId, 1L);

        assertThat(bookedCount()).isZero();
        assertThat(orderMapper.getById(firstId).getStatus()).isEqualTo(3);
    }

    @Test
    void concurrentSameRequestIdReturnsOneOrderWithoutCapacityLeak() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Long> first = executor.submit(() -> createAfter(start, "it-same-request"));
            Future<Long> second = executor.submit(() -> createAfter(start, "it-same-request"));
            start.countDown();

            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(bookedCount()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from explore_order where request_id = 'it-same-request'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentExpirationReleasesCapacityOnceAndCreatesOneEvent() throws Exception {
        Long orderId = orderService.create(itemOrder("it-expire-race", 2), 1L);
        jdbcTemplate.update("update explore_order set expire_at = date_sub(now(), interval 1 minute) where id = ?", orderId);
        LocalDateTime now = LocalDateTime.now();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> expireAfter(start, orderId, now));
            Future<Boolean> second = executor.submit(() -> expireAfter(start, orderId, now));
            start.countDown();

            assertThat(new boolean[]{first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
            ExploreOrder expired = orderMapper.getById(orderId);
            assertThat(expired.getStatus()).isEqualTo(4);
            assertThat(expired.getCancelType()).isEqualTo("TIMEOUT");
            assertThat(bookedCount()).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from order_event_outbox where aggregate_id = ? and event_type = 'ORDER_EXPIRED'",
                    Integer.class, orderId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void capacityReleaseFailureRollsBackExpiredStatusAndOutbox() {
        Long orderId = orderService.create(itemOrder("it-expire-rollback", 2), 1L);
        jdbcTemplate.update("update explore_order set item_id = null, expire_at = date_sub(now(), interval 1 minute) where id = ?", orderId);

        assertThatThrownBy(() -> expiredOrderProcessor.expire(orderId, LocalDateTime.now()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("释放");

        assertThat(orderMapper.getById(orderId).getStatus()).isZero();
        assertThat(bookedCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from order_event_outbox where aggregate_id = ?",
                Integer.class, orderId)).isZero();
    }

    @Test
    void failedOutboxDeliveryRetriesAndCreatesOneNotification() {
        Long orderId = orderService.create(itemOrder("it-outbox-retry", 1), 1L);
        orderService.updateStatus(orderId, 1);
        Long eventId = jdbcTemplate.queryForObject(
                "select id from order_event_outbox where aggregate_id = ? and event_type = 'ORDER_CONFIRMED'",
                Long.class, orderId);
        LocalDateTime now = outboxMapper.getById(eventId).getNextRetryAt();

        String retryLease = "it-retry-lease";
        assertThat(outboxTransactionService.claim(eventId, retryLease, now, now.plusMinutes(1))).isTrue();
        assertThat(outboxTransactionService.recordFailure(
                eventId, retryLease, new IllegalStateException("temporary downstream failure"), now)).isFalse();
        LocalDateTime retryAt = outboxMapper.getById(eventId).getNextRetryAt();
        assertThat(retryAt).isAfter(now);

        assertThat(outboxEventProcessor.process(eventId, retryAt, 60)).isTrue();
        assertThat(outboxEventProcessor.process(eventId, retryAt, 60)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_notification where order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from order_event_outbox where id = ?",
                String.class, eventId)).isEqualTo("PROCESSED");
    }

    @Test
    void staleOutboxLeaseCannotCommitAfterAnotherWorkerReclaimsEvent() {
        Long orderId = orderService.create(itemOrder("it-outbox-lease", 1), 1L);
        orderService.updateStatus(orderId, 1);
        Long eventId = jdbcTemplate.queryForObject(
                "select id from order_event_outbox where aggregate_id = ? and event_type = 'ORDER_CONFIRMED'",
                Long.class, orderId);
        LocalDateTime readyAt = outboxMapper.getById(eventId).getNextRetryAt();

        assertThat(outboxTransactionService.claim(
                eventId, "old-lease", readyAt, readyAt.plusSeconds(1))).isTrue();
        assertThat(outboxTransactionService.claim(
                eventId, "new-lease", readyAt.plusSeconds(2), readyAt.plusMinutes(1))).isTrue();

        assertThatThrownBy(() -> outboxTransactionService.deliver(
                eventId, "old-lease", readyAt.plusSeconds(2)))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("租约");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_notification where order_id = ?",
                Integer.class, orderId)).isZero();

        outboxTransactionService.deliver(eventId, "new-lease", readyAt.plusSeconds(2));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from user_notification where order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void concurrentUserCancelAndExpirationReleaseCapacityAndCreateOneEvent() throws Exception {
        Long orderId = orderService.create(itemOrder("it-cancel-expire-race", 2), 1L);
        jdbcTemplate.update(
                "update explore_order set expire_at = date_sub(now(), interval 1 minute) where id = ?", orderId);
        LocalDateTime now = LocalDateTime.now();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> cancel = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    orderService.cancelByUser(orderId, 1L);
                    return true;
                } catch (BaseException ex) {
                    return false;
                }
            });
            Future<Boolean> expire = executor.submit(() -> expireAfter(start, orderId, now));
            start.countDown();

            assertThat(new boolean[]{cancel.get(20, TimeUnit.SECONDS), expire.get(20, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(true, false);
            assertThat(orderMapper.getById(orderId).getStatus()).isIn(3, 4);
            assertThat(bookedCount()).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from order_event_outbox where aggregate_id = ?",
                    Integer.class, orderId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void authenticatedHttpFlowIsIdempotentExpiresAndExposesNotification() {
        Map<String, Object> loginBody = new HashMap<>();
        loginBody.put("phone", "13800001111");
        loginBody.put("password", "123456");
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
                "/user/user/login", loginBody, Map.class);
        assertThat(loginResponse.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String token = String.valueOf(loginData.get("token"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authentication", token);
        headers.set("X-Request-Id", "it-http-expiration-trace");
        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("requestId", "it-http-expiration");
        orderBody.put("orderType", 1);
        orderBody.put("itemId", 1001L);
        orderBody.put("peopleCount", 2);
        orderBody.put("contactName", "集成测试");
        orderBody.put("contactPhone", "13800001111");
        orderBody.put("reserveTime", LocalDateTime.now().plusDays(1)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        HttpEntity<Map<String, Object>> createRequest = new HttpEntity<>(orderBody, headers);

        ResponseEntity<Map> firstCreate = restTemplate.postForEntity(
                "/user/explore-order", createRequest, Map.class);
        ResponseEntity<Map> repeatedCreate = restTemplate.postForEntity(
                "/user/explore-order", createRequest, Map.class);
        assertThat(firstCreate.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(repeatedCreate.getBody().get("data")).isEqualTo(firstCreate.getBody().get("data"));
        assertThat(firstCreate.getHeaders().getFirst("X-Request-Id"))
                .isEqualTo("it-http-expiration-trace");
        Long orderId = ((Number) firstCreate.getBody().get("data")).longValue();
        assertThat(bookedCount()).isEqualTo(2);

        jdbcTemplate.update("update explore_order set expire_at = date_sub(now(), interval 1 minute) where id = ?", orderId);
        LocalDateTime expiredAt = LocalDateTime.now();
        assertThat(expiredOrderProcessor.expire(orderId, expiredAt)).isTrue();
        assertThat(bookedCount()).isZero();

        Long eventId = jdbcTemplate.queryForObject(
                "select id from order_event_outbox where aggregate_id = ? and event_type = 'ORDER_EXPIRED'",
                Long.class, orderId);
        LocalDateTime readyAt = outboxMapper.getById(eventId).getNextRetryAt();
        assertThat(outboxEventProcessor.process(eventId, readyAt, 60)).isTrue();

        ResponseEntity<Map> unread = restTemplate.exchange(
                "/user/notification/unread-count", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(((Number) unread.getBody().get("data")).longValue()).isEqualTo(1L);
        ResponseEntity<Map> notifications = restTemplate.exchange(
                "/user/notification/page?page=1&pageSize=10", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        Map<String, Object> notificationPage =
                (Map<String, Object>) notifications.getBody().get("data");
        List<Map<String, Object>> records =
                (List<Map<String, Object>>) notificationPage.get("records");
        assertThat(records).hasSize(1);
        assertThat(((Number) records.get(0).get("orderId")).longValue()).isEqualTo(orderId);
        assertThat(records.get(0).get("title")).isEqualTo("预约已超时取消");

        ResponseEntity<Map> orderDetail = restTemplate.exchange(
                "/user/explore-order/" + orderId, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        Map<String, Object> orderData = (Map<String, Object>) orderDetail.getBody().get("data");
        assertThat(((Number) orderData.get("status")).intValue()).isEqualTo(4);
        assertThat(orderData.get("cancelType")).isEqualTo("TIMEOUT");
    }

    @Test
    void concurrentBookingNeverExceedsDatabaseCapacity() throws Exception {
        jdbcTemplate.update("update explore_item set capacity = 1, booked = 0 where id = 1001");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> createAfter(start, "it-race-a")));
            futures.add(executor.submit(() -> createAfter(start, "it-race-b")));
            start.countDown();

            int success = 0;
            int rejected = 0;
            Long successfulOrderId = null;
            for (Future<Long> future : futures) {
                try {
                    successfulOrderId = future.get(20, TimeUnit.SECONDS);
                    success++;
                } catch (Exception ex) {
                    assertThat(rootCause(ex)).isInstanceOf(BaseException.class);
                    rejected++;
                }
            }

            assertThat(success).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(bookedCount()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from explore_order where request_id in ('it-race-a','it-race-b')",
                    Integer.class)).isEqualTo(1);

            orderService.cancelByUser(successfulOrderId, 1L);
            assertThat(bookedCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compareAndSetStatusRejectsStaleWriter() {
        Long orderId = orderService.create(itemOrder("it-cas", 1), 1L);

        int first = orderMapper.updateStatusIfCurrent(orderId, 0, 1, null, null, LocalDateTime.now());
        int stale = orderMapper.updateStatusIfCurrent(orderId, 0, 3, "ADMIN", "管理员取消预约", LocalDateTime.now());

        assertThat(first).isEqualTo(1);
        assertThat(stale).isZero();
        assertThat(orderMapper.getById(orderId).getStatus()).isEqualTo(1);
    }

    @Test
    void foreignKeyRejectsOrderForMissingUser() {
        ExploreOrder order = ExploreOrder.builder()
                .userId(999999L)
                .orderNo("IT-FK-" + System.nanoTime())
                .orderType(1)
                .itemId(1001L)
                .itemName("城市咖啡体验")
                .amount(new BigDecimal("39.00"))
                .peopleCount(1)
                .contactName("集成测试")
                .contactPhone("13800001111")
                .reserveTime(LocalDateTime.now().plusDays(1))
                .requestId("it-fk")
                .remark("")
                .status(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> orderMapper.insert(order))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueRequestConstraintRejectsDuplicateOrder() {
        Long firstOrderId = orderService.create(itemOrder("it-unique", 1), 1L);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into explore_order "
                        + "(user_id, order_no, order_type, item_id, package_id, item_name, amount, "
                        + "people_count, contact_name, contact_phone, reserve_time, request_id, remark, "
                        + "status, create_time, update_time) "
                        + "select user_id, concat('IT-DUP-', id), order_type, item_id, package_id, "
                        + "item_name, amount, people_count, contact_name, contact_phone, reserve_time, "
                        + "request_id, remark, status, now(), now() from explore_order where id = ?",
                firstOrderId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from explore_order where user_id = 1 and request_id = 'it-unique'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void observabilityEndpointsExposeHealthHttpTimingAndBookingCounters() {
        ResponseEntity<String> shopResponse = restTemplate.getForEntity(
                "/user/shop/status", String.class);

        assertThat(shopResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(shopResponse.getHeaders().getFirst("X-Request-Id")).isNotBlank();

        orderService.create(itemOrder("it-observability", 1), 1L);

        ResponseEntity<String> healthResponse = restTemplate.getForEntity(
                "/actuator/health", String.class);
        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
                "/actuator/prometheus", String.class);

        assertThat(healthResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(healthResponse.getBody()).contains("\"status\"");
        assertThat(metricsResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(metricsResponse.getBody())
                .contains("http_server_requests_seconds")
                .contains("local_explorer_booking_created_total");
    }

    @Test
    void swaggerDocumentationStartsAlongsideActuator() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/v2/api-docs?group={group}", String.class, "商家后台接口");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("\"swagger\":\"2.0\"")
                .contains("\"paths\"");
    }

    private Long createAfter(CountDownLatch start, String requestId) throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        return orderService.create(itemOrder(requestId, 1), 1L);
    }

    private boolean expireAfter(CountDownLatch start, Long orderId, LocalDateTime now)
            throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        return expiredOrderProcessor.expire(orderId, now);
    }

    private ExploreOrderDTO itemOrder(String requestId, int peopleCount) {
        ExploreOrderDTO dto = new ExploreOrderDTO();
        dto.setOrderType(1);
        dto.setItemId(1001L);
        dto.setPeopleCount(peopleCount);
        dto.setContactName("集成测试");
        dto.setContactPhone("13800001111");
        dto.setReserveTime(LocalDateTime.now().plusDays(1));
        dto.setRequestId(requestId);
        return dto;
    }

    private int bookedCount() {
        return jdbcTemplate.queryForObject(
                "select booked from explore_item where id = 1001", Integer.class);
    }

    private int countMetadata(String table, String condition) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + condition, Integer.class);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
