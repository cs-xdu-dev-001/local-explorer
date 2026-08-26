package com.localexplorer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportFileFormat;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.exception.ExportLimitExceededException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ExportFileGenerator {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int XLSX_MAX_TEXT = 32767;

    @Autowired private ExportDataReader dataReader;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ExportJobProperties properties;
    @Autowired private Clock clock;
    @Autowired private ExportSnapshotCipher snapshotCipher;

    public long generate(ExportJob job, Path target, ExportExecutionControl control) throws IOException {
        ExportQuerySnapshot snapshot = readSnapshot(job);
        ExportFileFormat format = ExportFileFormat.valueOf(job.getFileFormat());
        if (format == ExportFileFormat.CSV) {
            return writeCsv(snapshot, target, control);
        }
        return writeXlsx(snapshot, target, control);
    }

    private long writeCsv(ExportQuerySnapshot snapshot, Path target, ExportExecutionControl control) throws IOException {
        LocalDateTime started = now();
        long processed = 0;
        long lastId = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeCsvRow(writer, snapshot.getColumns());
            control.checkpoint(0);
            while (true) {
                ExportChunk chunk = dataReader.fetch(snapshot, lastId, properties.getBatchSize());
                if (chunk.getRows().isEmpty()) break;
                for (ExportRow row : chunk.getRows()) {
                    writeCsvRow(writer, row.getCells());
                }
                processed += chunk.getRows().size();
                lastId = chunk.getLastId();
                writer.flush();
                enforceLimits(target, started);
                control.checkpoint(processed);
            }
        }
        return processed;
    }

    private long writeXlsx(ExportQuerySnapshot snapshot, Path target, ExportExecutionControl control) throws IOException {
        LocalDateTime started = now();
        long processed = 0;
        long lastId = 0;
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try {
            SXSSFSheet sheet = workbook.createSheet("导出数据");
            int rowIndex = 0;
            writeXlsxRow(sheet.createRow(rowIndex++), snapshot.getColumns());
            sheet.createFreezePane(0, 1);
            control.checkpoint(0);
            while (true) {
                ExportChunk chunk = dataReader.fetch(snapshot, lastId, properties.getBatchSize());
                if (chunk.getRows().isEmpty()) break;
                for (ExportRow row : chunk.getRows()) {
                    writeXlsxRow(sheet.createRow(rowIndex++), row.getCells());
                }
                processed += chunk.getRows().size();
                lastId = chunk.getLastId();
                control.checkpoint(processed);
                enforceRuntime(started);
            }
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
            enforceLimits(target, started);
            return processed;
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void writeCsvRow(BufferedWriter writer, List<?> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write(',');
            String value = safeSpreadsheetText(format(cells.get(i)));
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.newLine();
    }

    private void writeXlsxRow(Row row, List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            Cell cell = row.createCell(i);
            if (value instanceof Number && !(value instanceof BigDecimal)) {
                cell.setCellValue(((Number) value).doubleValue());
            } else if (value instanceof BigDecimal) {
                cell.setCellValue(((BigDecimal) value).doubleValue());
            } else {
                String text = safeSpreadsheetText(format(value));
                cell.setCellValue(text.length() <= XLSX_MAX_TEXT ? text : text.substring(0, XLSX_MAX_TEXT));
            }
        }
    }

    private String format(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime) return DATE_TIME.format((LocalDateTime) value);
        if (value instanceof LocalDate) return value.toString();
        return cleanXmlText(String.valueOf(value));
    }

    private String safeSpreadsheetText(String value) {
        if (value == null || value.isEmpty()) return "";
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private String cleanXmlText(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) result.append(c);
        }
        return result.toString();
    }

    private ExportQuerySnapshot readSnapshot(ExportJob job) throws IOException {
        ExportQuerySnapshot snapshot = objectMapper.readValue(job.getQuerySnapshot(), ExportQuerySnapshot.class);
        snapshotCipher.reveal(snapshot);
        return snapshot;
    }

    private void enforceLimits(Path target, LocalDateTime started) throws IOException {
        enforceRuntime(started);
        if (Files.exists(target) && Files.size(target) > properties.getMaxFileBytes()) {
            throw new ExportLimitExceededException(ExportLimitExceededException.FILE_TOO_LARGE,
                    "导出文件超过大小限制");
        }
    }

    private void enforceRuntime(LocalDateTime started) throws IOException {
        if (Duration.between(started, now()).getSeconds() > properties.getMaxRuntimeSeconds()) {
            throw new ExportLimitExceededException(ExportLimitExceededException.RUNTIME_EXCEEDED,
                    "导出任务超过最大执行时间");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
