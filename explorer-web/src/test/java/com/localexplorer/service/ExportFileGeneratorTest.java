package com.localexplorer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.ExportJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.io.InputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportFileGeneratorTest {

    private ExportFileGenerator generator;
    private ObjectMapper objectMapper;
    @Mock private ExportDataReader dataReader;
    @Mock private ExportExecutionControl control;
    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ExportJobProperties properties = new ExportJobProperties();
        properties.setBatchSize(2);
        generator = new ExportFileGenerator();
        ReflectionTestUtils.setField(generator, "dataReader", dataReader);
        ReflectionTestUtils.setField(generator, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(generator, "properties", properties);
        ExportSnapshotCipher snapshotCipher = new ExportSnapshotCipher();
        ReflectionTestUtils.setField(snapshotCipher, "properties", properties);
        ReflectionTestUtils.setField(snapshotCipher, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(generator, "snapshotCipher", snapshotCipher);
        ReflectionTestUtils.setField(generator, "clock", Clock.fixed(
                Instant.parse("2026-08-24T08:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    @Test
    void streamsCsvByKeysetAndNeutralizesSpreadsheetFormulas() throws Exception {
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().exportType("ORDER").fileFormat("CSV")
                .maxId(2L).columns(Arrays.asList("名称", "金额", "时间")).build();
        ExportJob job = ExportJob.builder().jobId("job-csv").fileFormat("CSV").exportType("ORDER")
                .querySnapshot(objectMapper.writeValueAsString(snapshot)).totalRows(2L).build();
        ExportChunk first = new ExportChunk(Arrays.asList(
                new ExportRow(1L, Arrays.asList("=CMD()", new BigDecimal("12.30"), LocalDateTime.of(2026, 8, 24, 9, 0))),
                new ExportRow(2L, Arrays.asList("正常,中文", null, LocalDateTime.of(2026, 8, 24, 10, 0)))
        ), 2L);
        when(dataReader.fetch(any(), eq(0L), eq(2))).thenReturn(first);
        when(dataReader.fetch(any(), eq(2L), eq(2))).thenReturn(new ExportChunk(Collections.emptyList(), 2L));
        Path file = tempDir.resolve("rows.csv.part");

        long rows = generator.generate(job, file, control);

        String csv = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertThat(rows).isEqualTo(2);
        assertThat(csv).startsWith("\uFEFF").contains("'=CMD()", "\"正常,中文\"", "12.30", "2026-08-24 09:00:00");
        verify(control).checkpoint(2L);
    }

    @Test
    void writesTypedXlsxAndCleansInvalidOrOversizedText() throws Exception {
        String longText = repeat("长", 33000);
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().exportType("ORDER").fileFormat("XLSX")
                .maxId(1L).columns(Arrays.asList("名称", "金额", "空值", "长文本")).build();
        ExportJob job = ExportJob.builder().jobId("job-xlsx").fileFormat("XLSX").exportType("ORDER")
                .querySnapshot(objectMapper.writeValueAsString(snapshot)).totalRows(1L).build();
        when(dataReader.fetch(any(), eq(0L), eq(2))).thenReturn(new ExportChunk(Collections.singletonList(
                new ExportRow(1L, Arrays.asList("\u0001 =CMD()", new BigDecimal("12.30"), null, longText))), 1L));
        when(dataReader.fetch(any(), eq(1L), eq(2))).thenReturn(new ExportChunk(Collections.emptyList(), 1L));
        Path file = tempDir.resolve("rows.xlsx.part");

        assertThat(generator.generate(job, file, control)).isEqualTo(1L);

        try (InputStream input = Files.newInputStream(file); XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            org.apache.poi.ss.usermodel.Row row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("' =CMD()");
            assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(12.30D);
            assertThat(row.getCell(2).getStringCellValue()).isEmpty();
            assertThat(row.getCell(3).getStringCellValue()).hasSize(32767);
        }
    }

    @Test
    void stopsAtBatchCheckpointWhenMaximumRuntimeIsExceeded() throws Exception {
        ExportJobProperties properties = (ExportJobProperties) ReflectionTestUtils.getField(generator, "properties");
        properties.setMaxRuntimeSeconds(1);
        MutableClock mutableClock = new MutableClock(Instant.parse("2026-08-24T08:00:00Z"));
        ReflectionTestUtils.setField(generator, "clock", mutableClock);
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().exportType("ORDER").fileFormat("CSV")
                .maxId(1L).columns(Collections.singletonList("名称")).build();
        ExportJob job = ExportJob.builder().jobId("job-timeout").fileFormat("CSV").exportType("ORDER")
                .querySnapshot(objectMapper.writeValueAsString(snapshot)).totalRows(1L).build();
        when(dataReader.fetch(any(), eq(0L), eq(2))).thenAnswer(invocation -> {
            mutableClock.advance(Duration.ofSeconds(2));
            return new ExportChunk(Collections.singletonList(new ExportRow(1L,
                    Collections.<Object>singletonList("row"))), 1L);
        });

        assertThatThrownBy(() -> generator.generate(job, tempDir.resolve("timeout.csv.part"), control))
                .isInstanceOf(IOException.class)
                .hasMessage("导出任务超过最大执行时间");
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
