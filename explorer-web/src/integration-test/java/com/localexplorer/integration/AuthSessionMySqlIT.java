package com.localexplorer.integration;

import com.localexplorer.entity.AuthSession;
import com.localexplorer.exception.BaseException;
import com.localexplorer.mapper.AuthSessionMapper;
import com.localexplorer.mapper.LoginGuardMapper;
import com.localexplorer.service.AuthSessionService;
import com.localexplorer.service.IssuedAuthSession;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import com.localexplorer.service.impl.LoginProtectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AuthSessionMySqlIT {

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

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthSessionService service;
    @Autowired private AuthSessionMapper mapper;
    @Autowired private LoginGuardMapper loginGuardMapper;
    @Autowired private LoginProtectionService loginProtectionService;

    @BeforeEach
    void reset() {
        dropFailureTrigger();
        jdbc.update("delete from login_guard");
        jdbc.update("delete from auth_session");
    }

    @AfterEach
    void cleanTrigger() { dropFailureTrigger(); }

    @Test
    void schemaUsesDigestUniquenessAndOperationalIndexes() {
        IssuedAuthSession issued = service.issue("EMPLOYEE", 1L, "hashed-ip", "Chrome / Windows");
        AuthSession stored = mapper.getBySessionId(issued.getSessionId());

        assertThat(stored.getRefreshTokenHash()).isEqualTo(AuthSessionServiceImpl.hash(issued.getRefreshToken()));
        assertThat(stored.getRefreshTokenHash()).doesNotContain(issued.getRefreshToken());
        assertThat(metadata("information_schema.statistics",
                "table_schema=database() and table_name='auth_session' and index_name in " +
                        "('uk_auth_refresh_hash','idx_auth_principal','idx_auth_expiry','idx_auth_family')"))
                .isGreaterThanOrEqualTo(4);

        assertThatThrownBy(() -> jdbc.update(
                "insert into auth_session(session_id,token_family_id,principal_type,principal_id," +
                        "refresh_token_hash,status,expires_at,create_time,update_time) " +
                        "select 'duplicate-hash',token_family_id,principal_type,principal_id,refresh_token_hash," +
                        "status,expires_at,create_time,update_time from auth_session where session_id=?",
                issued.getSessionId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentRotationConsumesOldTokenOnceAndKeepsOneSuccessorActive() throws Exception {
        IssuedAuthSession issued = service.issue("EMPLOYEE", 1L, "ip", "device");
        String family = mapper.getBySessionId(issued.getSessionId()).getTokenFamilyId();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.rotate("EMPLOYEE", issued.getRefreshToken(), "ip", "device");
                        return true;
                    } catch (BaseException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            assertThat(new boolean[]{futures.get(0).get(20, TimeUnit.SECONDS),
                    futures.get(1).get(20, TimeUnit.SECONDS)}).containsExactlyInAnyOrder(true, false);
            assertThat(count("select count(*) from auth_session where token_family_id=? and status='ACTIVE'", family))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void replayAfterRaceGraceRevokesWholeTokenFamily() throws Exception {
        IssuedAuthSession issued = service.issue("USER", 1L, "ip", "device");
        String family = mapper.getBySessionId(issued.getSessionId()).getTokenFamilyId();
        service.rotate("USER", issued.getRefreshToken(), "ip", "device");

        Thread.sleep(2600L);
        assertThatThrownBy(() -> service.rotate("USER", issued.getRefreshToken(), "ip", "device"))
                .isInstanceOf(BaseException.class);

        assertThat(count("select count(*) from auth_session where token_family_id=? and status='ACTIVE'", family))
                .isZero();
        assertThat(count("select count(*) from auth_session where token_family_id=? and revoke_reason='REFRESH_REPLAY'", family))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void successorInsertFailureRollsBackOldTokenConsumption() {
        IssuedAuthSession issued = service.issue("EMPLOYEE", 1L, "ip", "device");
        String family = mapper.getBySessionId(issued.getSessionId()).getTokenFamilyId();
        jdbc.execute("create trigger fail_auth_successor before insert on auth_session for each row " +
                "begin if new.token_family_id='" + family + "' then signal sqlstate '45000' " +
                "set message_text='forced successor failure'; end if; end");

        assertThatThrownBy(() -> service.rotate("EMPLOYEE", issued.getRefreshToken(), "ip", "device"))
                .isInstanceOf(RuntimeException.class);

        assertThat(mapper.getBySessionId(issued.getSessionId()).getStatus()).isEqualTo("ACTIVE");
        dropFailureTrigger();
        assertThat(service.rotate("EMPLOYEE", issued.getRefreshToken(), "ip", "device").getAccessToken()).isNotBlank();
    }

    @Test
    void concurrentLoginFailuresDoNotLoseCountsAndLockAtThreshold() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    loginProtectionService.recordFailure("USER", "account-hash", "ip-hash", "138****1111");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);

            assertThat(loginGuardMapper.find("USER", "account-hash", "ip-hash").getFailedCount()).isEqualTo(5);
            assertThat(loginGuardMapper.find("USER", "account-hash", "ip-hash").getLockedUntil()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    private int metadata(String table, String where) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
    }

    private int count(String sql, Object value) { return jdbc.queryForObject(sql, Integer.class, value); }

    private void dropFailureTrigger() {
        try { jdbc.execute("drop trigger if exists fail_auth_successor"); } catch (RuntimeException ignored) { }
    }
}
