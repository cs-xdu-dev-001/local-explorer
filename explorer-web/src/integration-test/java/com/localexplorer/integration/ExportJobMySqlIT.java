package com.localexplorer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.dto.CreateExportJobDTO;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.mapper.ExportJobMapper;
import com.localexplorer.service.ExportSnapshotPlan;
import com.localexplorer.service.ExportSnapshotService;
import com.localexplorer.service.impl.ExportJobProcessor;
import com.localexplorer.storage.ExportFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ExportJobMySqlIT {

    private static final String STORAGE_ROOT = System.getProperty("java.io.tmpdir")
            + "/local-explorer-export-it-" + UUID.randomUUID().toString().replace("-", "");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("local_explorer")
            .withUsername("root")
            .withPassword("test-password")
            .withInitScript("local-explorer-init.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("explorer.datasource.host", MYSQL::getHost);
        registry.add("explorer.datasource.port", () -> MYSQL.getMappedPort(3306));
        registry.add("explorer.datasource.database", MYSQL::getDatabaseName);
        registry.add("explorer.datasource.username", MYSQL::getUsername);
        registry.add("explorer.datasource.password", MYSQL::getPassword);
        registry.add("explorer.export.storage-root", () -> STORAGE_ROOT);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ExportJobMapper exportJobMapper;
    @Autowired private ExportJobProcessor processor;
    @Autowired private ExportFileStorage fileStorage;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext applicationContext;
    @Autowired private ExportSnapshotService snapshotService;

    @BeforeEach
    void cleanBefore() throws Exception {
        cleanupFilesAndRows();
    }

    @AfterEach
    void cleanAfter() throws Exception {
        cleanupFilesAndRows();
    }

    @Test
    void schemaUsesUniqueForeignKeyAndReadyLeaseIndexes() {
        assertThat(countMetadata("information_schema.statistics",
                "table_schema = database() and table_name = 'export_job' " +
                        "and index_name in ('uk_export_operator_request','idx_export_ready','idx_export_lease')"))
                .isEqualTo(8);
        assertThat(countMetadata("information_schema.referential_constraints",
                "constraint_schema = database() and table_name = 'export_job' " +
                        "and constraint_name = 'fk_export_operator'"))
                .isEqualTo(1);

        insertJob("it-export-index", "it-export-index", "PENDING", LocalDateTime.now().minusMinutes(1), null);
        Map<String, Object> pendingPlan = jdbcTemplate.queryForMap(
                "explain select job_id from export_job force index(idx_export_ready) where status = 'PENDING' " +
                        "and next_retry_at <= now() order by next_retry_at, job_id limit 10");
        Map<String, Object> leasePlan = jdbcTemplate.queryForMap(
                "explain select job_id from export_job force index(idx_export_lease) where status = 'RUNNING' " +
                        "and lease_until <= now() order by lease_until, job_id limit 10");
        Map<String, Object> staffListPlan = jdbcTemplate.queryForMap(
                "explain select job_id from export_job force index(idx_export_operator_list) " +
                        "where operator_id=1 order by create_time desc, job_id desc limit 20");
        Map<String, Object> adminListPlan = jdbcTemplate.queryForMap(
                "explain select job_id from export_job force index(idx_export_admin_list) " +
                        "order by create_time desc, job_id desc limit 20");
        assertThat(pendingPlan.get("key")).isEqualTo("idx_export_ready");
        assertThat(leasePlan.get("key")).isEqualTo("idx_export_lease");
        assertThat(staffListPlan.get("key")).isEqualTo("idx_export_operator_list");
        assertThat(adminListPlan.get("key")).isEqualTo("idx_export_admin_list");
    }

    @Test
    void uniqueRequestAndEmployeeForeignKeyAreEnforced() {
        insertJob("it-export-unique-a", "it-export-same", "PENDING", LocalDateTime.now(), null);

        assertThatThrownBy(() -> insertJob("it-export-unique-b", "it-export-same", "PENDING",
                LocalDateTime.now(), null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into export_job(job_id,request_id,export_type,file_format,query_snapshot,status,progress," +
                        "total_rows,processed_rows,retry_count,next_retry_at,operator_id,create_time,update_time) " +
                        "values('it-export-bad-fk','it-export-bad-fk','ORDER','CSV','{}','PENDING',0,0,0,0,now(),999999,now(),now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoWorkersCanClaimSameJobOnlyOnce() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        insertJob("it-export-race", "it-export-race", "PENDING", now.minusSeconds(1), null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> claimAfter(start, "node-a", now));
            Future<Integer> second = pool.submit(() -> claimAfter(start, "node-b", now));
            start.countDown();

            assertThat(Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1, 0);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from export_job where job_id='it-export-race' and status='RUNNING'",
                    Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseIsRecoverableAndOldOwnerCannotCheckpoint() {
        LocalDateTime now = LocalDateTime.now();
        insertJob("it-export-recover", "it-export-recover", "RUNNING", now.minusMinutes(2), now.minusMinutes(1));

        assertThat(exportJobMapper.claim("it-export-recover", "node-new", now, now.plusMinutes(1))).isEqualTo(1);
        assertThat(exportJobMapper.updateProgress("it-export-recover", "node-old", 10, 50,
                now, now.plusMinutes(1))).isZero();
        assertThat(exportJobMapper.updateProgress("it-export-recover", "node-new", 10, 50,
                now, now.plusMinutes(1))).isEqualTo(1);
    }

    @Test
    void realOrderExportCompletesWithMaskedDataAndVerifiedChecksum() throws Exception {
        Long total = jdbcTemplate.queryForObject("select count(*) from explore_order", Long.class);
        insertJob("it-export-e2e", "it-export-e2e", "PENDING", LocalDateTime.now().minusSeconds(1), null,
                objectMapper.writeValueAsString(orderSnapshot("CSV")), total);

        assertThat(processor.process("it-export-e2e")).isTrue();

        ExportJob completed = exportJobMapper.getByJobId("it-export-e2e");
        assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(completed.getProcessedRows()).isEqualTo(total);
        assertThat(fileStorage.inspect(completed.getFilePath()).getChecksum()).isEqualTo(completed.getChecksum());
        String csv = new String(Files.readAllBytes(fileStorage.resolve(completed.getFilePath())), StandardCharsets.UTF_8);
        assertThat(csv).contains("138****1111").doesNotContain("13800001111", "password", "token");
    }

    @Test
    void userExportEncryptsPiiFilterAndWritesOnlyMaskedPhone() throws Exception {
        String phone = jdbcTemplate.queryForObject("select phone from user order by id limit 1", String.class);
        CreateExportJobDTO request = new CreateExportJobDTO();
        request.setRequestId("it-export-user-pii");
        request.setExportType("USER");
        request.setFileFormat("CSV");
        request.setPhone(phone);
        ExportSnapshotPlan plan = snapshotService.freeze(request, LocalDateTime.now());
        String snapshot = objectMapper.writeValueAsString(plan.getSnapshot());
        assertThat(snapshot).contains("encryptedPii").doesNotContain(phone, "\"phone\"");
        insertJob("it-export-user-pii", "it-export-user-pii", "PENDING",
                LocalDateTime.now().minusSeconds(1), null, snapshot, plan.getTotalRows(), "USER", "CSV");

        assertThat(processor.process("it-export-user-pii")).isTrue();

        ExportJob completed = exportJobMapper.getByJobId("it-export-user-pii");
        String csv = new String(Files.readAllBytes(fileStorage.resolve(completed.getFilePath())), StandardCharsets.UTF_8);
        assertThat(csv).contains(phone.substring(0, 3) + "****" + phone.substring(7))
                .doesNotContain(phone, "password", "token", "refreshToken");
        assertThat(jdbcTemplate.queryForObject(
                "select query_snapshot from export_job where job_id='it-export-user-pii'", String.class))
                .doesNotContain(phone);
    }

    @Test
    void expiredRunningJobIsRecoveredAndEventuallyCompletes() throws Exception {
        Long total = jdbcTemplate.queryForObject("select count(*) from explore_order", Long.class);
        insertJob("it-export-crash", "it-export-crash", "RUNNING", LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().minusMinutes(1), objectMapper.writeValueAsString(orderSnapshot("CSV")), total);

        assertThat(processor.process("it-export-crash")).isTrue();

        ExportJob completed = exportJobMapper.getByJobId("it-export-crash");
        assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(completed.getProcessedRows()).isEqualTo(total);
        assertThat(fileStorage.inspect(completed.getFilePath()).getChecksum()).isEqualTo(completed.getChecksum());
    }

    @Test
    void concurrentExecutorsGenerateOnlyOneValidFile() throws Exception {
        Long total = jdbcTemplate.queryForObject("select count(*) from explore_order", Long.class);
        insertJob("it-export-process-race", "it-export-process-race", "PENDING",
                LocalDateTime.now().minusSeconds(1), null,
                objectMapper.writeValueAsString(orderSnapshot("XLSX")), total);
        ExportJobProcessor firstProcessor = applicationContext.getAutowireCapableBeanFactory()
                .createBean(ExportJobProcessor.class);
        ExportJobProcessor secondProcessor = applicationContext.getAutowireCapableBeanFactory()
                .createBean(ExportJobProcessor.class);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> processAfter(start, firstProcessor));
            Future<Boolean> second = pool.submit(() -> processAfter(start, secondProcessor));
            start.countDown();

            assertThat(Arrays.asList(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            ExportJob completed = exportJobMapper.getByJobId("it-export-process-race");
            assertThat(completed.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(fileStorage.inspect(completed.getFilePath()).getChecksum()).isEqualTo(completed.getChecksum());
            Path files = java.nio.file.Paths.get(STORAGE_ROOT, "files");
            try (java.util.stream.Stream<Path> stream = Files.list(files)) {
                assertThat(stream.filter(Files::isRegularFile).count()).isEqualTo(1L);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void streamsTenThousandRealMysqlRowsAndWritesPerformanceEvidence() throws Exception {
        final int rowCount = 10000;
        jdbcTemplate.batchUpdate(
                "insert into operation_log(description,operator_id,request_method,request_uri,client_ip,cost_time,create_time) " +
                        "values(?,1,'GET','/admin/export-smoke','masked',?,now())",
                new org.springframework.jdbc.core.BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement statement, int index) throws java.sql.SQLException {
                        statement.setString(1, "it-export-large-" + index);
                        statement.setLong(2, index % 100);
                    }

                    @Override
                    public int getBatchSize() { return rowCount; }
                });
        Long maxId = jdbcTemplate.queryForObject(
                "select max(id) from operation_log where description like 'it-export-large-%'", Long.class);
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().exportType("OPERATION_LOG").fileFormat("CSV")
                .description("it-export-large-").maxId(maxId)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(1))
                .columns(Arrays.asList("操作描述", "操作人", "请求方法", "请求路径", "耗时(ms)", "操作时间"))
                .sort("id ASC").snapshotAt(LocalDateTime.now()).build();
        insertJob("it-export-large", "it-export-large", "PENDING", LocalDateTime.now().minusSeconds(1), null,
                objectMapper.writeValueAsString(snapshot), (long) rowCount, "OPERATION_LOG", "CSV");
        AtomicBoolean sampling = new AtomicBoolean(true);
        long baseline = usedMemory();
        AtomicLong peak = new AtomicLong(baseline);
        Thread sampler = new Thread(() -> {
            while (sampling.get()) {
                peak.accumulateAndGet(usedMemory(), Math::max);
                try { Thread.sleep(5L); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }
        }, "export-memory-sampler");
        sampler.start();
        long started = System.nanoTime();
        boolean succeeded;
        try {
            succeeded = processor.process("it-export-large");
        } finally {
            sampling.set(false);
            sampler.join(5000L);
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        ExportJob completed = exportJobMapper.getByJobId("it-export-large");
        Path file = fileStorage.resolve(completed.getFilePath());
        try (java.util.stream.Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            assertThat(lines.count()).isEqualTo(rowCount + 1L);
        }
        assertThat(succeeded).isTrue();
        assertThat(completed.getProcessedRows()).isEqualTo((long) rowCount);
        assertThat(fileStorage.inspect(completed.getFilePath()).getChecksum()).isEqualTo(completed.getChecksum());
        assertThat(Math.max(0L, peak.get() - baseline)).isLessThan(384L * 1024 * 1024);

        ExportQuerySnapshot xlsxSnapshot = ExportQuerySnapshot.builder().exportType("OPERATION_LOG").fileFormat("XLSX")
                .description("it-export-large-").maxId(maxId)
                .startTime(LocalDateTime.now().minusDays(1)).endTime(LocalDateTime.now().plusDays(1))
                .columns(snapshot.getColumns()).sort("id ASC").snapshotAt(LocalDateTime.now()).build();
        insertJob("it-export-large-xlsx", "it-export-large-xlsx", "PENDING",
                LocalDateTime.now().minusSeconds(1), null, objectMapper.writeValueAsString(xlsxSnapshot),
                (long) rowCount, "OPERATION_LOG", "XLSX");
        long xlsxStarted = System.nanoTime();
        assertThat(processor.process("it-export-large-xlsx")).isTrue();
        long xlsxElapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - xlsxStarted);
        ExportJob xlsxCompleted = exportJobMapper.getByJobId("it-export-large-xlsx");
        Path xlsxFile = fileStorage.resolve(xlsxCompleted.getFilePath());
        try (java.io.InputStream input = Files.newInputStream(xlsxFile);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(rowCount);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("操作描述");
        }
        assertThat(xlsxCompleted.getProcessedRows()).isEqualTo((long) rowCount);
        assertThat(fileStorage.inspect(xlsxCompleted.getFilePath()).getChecksum())
                .isEqualTo(xlsxCompleted.getChecksum());

        Path reportDir = java.nio.file.Paths.get("target", "export-performance");
        Files.createDirectories(reportDir);
        Files.copy(file, reportDir.resolve("real-mysql-10000.csv"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(xlsxFile, reportDir.resolve("real-mysql-10000.xlsx"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        String report = String.format("{\"source\":\"Testcontainers MySQL 8\",\"rows\":%d," +
                        "\"elapsedMs\":%d,\"fileBytes\":%d,\"peakHeapDeltaBytes\":%d," +
                        "\"rowsPerSecond\":%.2f,\"sha256\":\"%s\"," +
                        "\"xlsxElapsedMs\":%d,\"xlsxFileBytes\":%d,\"xlsxSha256\":\"%s\"}%n",
                rowCount, elapsedMs, Files.size(file), Math.max(0L, peak.get() - baseline),
                rowCount / Math.max(0.001D, elapsedMs / 1000D), completed.getChecksum(),
                xlsxElapsedMs, Files.size(xlsxFile), xlsxCompleted.getChecksum());
        Files.write(reportDir.resolve("real-mysql-smoke.json"), report.getBytes(StandardCharsets.UTF_8));
    }

    private int claimAfter(CountDownLatch start, String owner, LocalDateTime now) throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        return exportJobMapper.claim("it-export-race", owner, now, now.plusMinutes(1));
    }

    private boolean processAfter(CountDownLatch start, ExportJobProcessor target) throws InterruptedException {
        start.await(10, TimeUnit.SECONDS);
        return target.process("it-export-process-race");
    }

    private ExportQuerySnapshot orderSnapshot(String format) {
        Long maxId = jdbcTemplate.queryForObject("select max(id) from explore_order", Long.class);
        return ExportQuerySnapshot.builder().exportType("ORDER").fileFormat(format).maxId(maxId)
                .startTime(LocalDateTime.of(2020, 1, 1, 0, 0)).endTime(LocalDateTime.of(2030, 1, 1, 0, 0))
                .columns(Arrays.asList("预约编号", "用户", "类型", "项目/套餐", "金额", "人数", "联系人",
                        "联系电话", "预约时间", "状态", "创建时间"))
                .sort("id ASC").snapshotAt(LocalDateTime.now()).build();
    }

    private void insertJob(String jobId, String requestId, String status, LocalDateTime nextRetryAt,
                           LocalDateTime leaseUntil) {
        insertJob(jobId, requestId, status, nextRetryAt, leaseUntil, "{}", 0L);
    }

    private void insertJob(String jobId, String requestId, String status, LocalDateTime nextRetryAt,
                           LocalDateTime leaseUntil, String snapshot, Long totalRows) {
        insertJob(jobId, requestId, status, nextRetryAt, leaseUntil, snapshot, totalRows, "ORDER", "CSV");
    }

    private void insertJob(String jobId, String requestId, String status, LocalDateTime nextRetryAt,
                           LocalDateTime leaseUntil, String snapshot, Long totalRows,
                           String exportType, String fileFormat) {
        ExportJob job = ExportJob.builder().jobId(jobId).requestId(requestId).exportType(exportType).fileFormat(fileFormat)
                .querySnapshot(snapshot).status(status).progress(0).totalRows(totalRows).processedRows(0L)
                .retryCount(0).nextRetryAt(nextRetryAt).leaseOwner("RUNNING".equals(status) ? "node-old" : null)
                .leaseUntil(leaseUntil).operatorId(1L).createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();
        exportJobMapper.insert(job);
        if (leaseUntil != null) {
            jdbcTemplate.update("update export_job set lease_owner='node-old', lease_until=? where job_id=?",
                    leaseUntil, jobId);
        }
    }

    private void cleanupFilesAndRows() throws Exception {
        List<String> paths = jdbcTemplate.queryForList(
                "select file_path from export_job where job_id like 'it-export-%' and file_path is not null",
                String.class);
        for (String path : paths) fileStorage.delete(path);
        jdbcTemplate.update("delete from export_job where job_id like 'it-export-%'");
        jdbcTemplate.update("delete from operation_log where description like 'it-export-large-%'");
    }

    private long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private int countMetadata(String table, String condition) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + condition, Integer.class);
    }
}
