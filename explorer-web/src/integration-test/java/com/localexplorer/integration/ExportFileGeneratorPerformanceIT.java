package com.localexplorer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.ExportJob;
import com.localexplorer.service.ExportChunk;
import com.localexplorer.service.ExportDataReader;
import com.localexplorer.service.ExportFileGenerator;
import com.localexplorer.service.ExportRow;
import com.localexplorer.service.ExportSnapshotCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFileGeneratorPerformanceIT {

    private static final int ROWS = 100000;
    @TempDir Path tempDir;

    @Test
    void streamsOneHundredThousandRowsToCsvAndXlsxWithBoundedChunks() throws Exception {
        List<String> reports = new ArrayList<>();
        for (String format : Arrays.asList("CSV", "XLSX")) {
            CountingReader reader = new CountingReader(ROWS);
            ExportFileGenerator generator = generator(reader);
            Path file = tempDir.resolve("export." + format.toLowerCase());
            ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().exportType("ORDER").fileFormat(format)
                    .maxId((long) ROWS).columns(Arrays.asList("ID", "名称", "金额", "备注")).build();
            ExportJob job = ExportJob.builder().jobId("perf-" + format).exportType("ORDER").fileFormat(format)
                    .querySnapshot(new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot))
                    .totalRows((long) ROWS).build();
            long before = retainedMemory();
            AtomicLong peak = new AtomicLong(before);
            AtomicLong sampledChunks = new AtomicLong();
            long started = System.nanoTime();

            long rows = generator.generate(job, file, ignored -> {
                if (sampledChunks.incrementAndGet() % 10 == 0) updateRetainedPeak(peak);
            });

            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            updateRetainedPeak(peak);
            long peakMemoryDelta = Math.max(0, peak.get() - before);
            String checksum = sha256(file);
            assertThat(rows).isEqualTo(ROWS);
            assertThat(reader.calls).isLessThanOrEqualTo(202);
            assertThat(reader.maxChunkRows).isLessThanOrEqualTo(500);
            assertThat(Files.size(file)).isGreaterThan(1024L);
            assertThat(peakMemoryDelta).isLessThan(256L * 1024 * 1024);
            assertThat(checksum).matches("[0-9a-f]{64}");
            assertFileCanBeParsed(format, file);
            Path reportDir = java.nio.file.Paths.get("target", "export-performance");
            Files.createDirectories(reportDir);
            Files.copy(file, reportDir.resolve("export-100000." + format.toLowerCase()),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            reports.add(String.format("{\"format\":\"%s\",\"rows\":%d,\"elapsedMs\":%d," +
                            "\"fileBytes\":%d,\"peakRetainedHeapDeltaBytes\":%d,\"rowsPerSecond\":%.2f," +
                            "\"chunkCalls\":%d,\"sha256\":\"%s\"}",
                    format, rows, elapsedMs, Files.size(file), peakMemoryDelta,
                    rows / Math.max(0.001D, elapsedMs / 1000D), reader.calls, checksum));
        }
        Path reportDir = java.nio.file.Paths.get("target", "export-performance");
        Files.createDirectories(reportDir);
        Files.write(reportDir.resolve("export-performance.json"),
                ("[\n" + String.join(",\n", reports) + "\n]\n").getBytes(StandardCharsets.UTF_8));
    }

    private ExportFileGenerator generator(ExportDataReader reader) {
        ExportJobProperties properties = new ExportJobProperties();
        properties.setBatchSize(500);
        properties.setMaxFileBytes(100L * 1024 * 1024);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ExportSnapshotCipher snapshotCipher = new ExportSnapshotCipher();
        ReflectionTestUtils.setField(snapshotCipher, "properties", properties);
        ReflectionTestUtils.setField(snapshotCipher, "objectMapper", objectMapper);
        ExportFileGenerator generator = new ExportFileGenerator();
        ReflectionTestUtils.setField(generator, "dataReader", reader);
        ReflectionTestUtils.setField(generator, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(generator, "properties", properties);
        ReflectionTestUtils.setField(generator, "snapshotCipher", snapshotCipher);
        ReflectionTestUtils.setField(generator, "clock", Clock.systemUTC());
        return generator;
    }

    private long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long retainedMemory() {
        System.gc();
        System.runFinalization();
        System.gc();
        try {
            Thread.sleep(20L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return usedMemory();
    }

    private void updateRetainedPeak(AtomicLong peak) {
        long current = retainedMemory();
        long previous;
        do {
            previous = peak.get();
            if (current <= previous) return;
        } while (!peak.compareAndSet(previous, current));
    }

    private void assertFileCanBeParsed(String format, Path file) throws Exception {
        if ("CSV".equals(format)) {
            try (java.util.stream.Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                assertThat(lines.count()).isEqualTo(ROWS + 1L);
            }
            try (java.io.BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                assertThat(reader.readLine()).isEqualTo("\uFEFF\"ID\",\"名称\",\"金额\",\"备注\"");
            }
            return;
        }
        XlsxEvidence evidence = readXlsxEvidence(file);
        assertThat(evidence.rows).isEqualTo(ROWS + 1L);
        assertThat(evidence.headers).containsExactly("ID", "名称", "金额", "备注");
    }

    private XlsxEvidence readXlsxEvidence(Path file) throws Exception {
        XlsxEvidence evidence = new XlsxEvidence();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            assertThat(sheet).isNotNull();
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.newSAXParser().parse(zip.getInputStream(sheet), new DefaultHandler() {
                private boolean headerText;
                private StringBuilder text;

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    if ("row".equals(qName)) evidence.rows++;
                    if (evidence.rows == 1 && "t".equals(qName)) {
                        headerText = true;
                        text = new StringBuilder();
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) {
                    if (headerText) text.append(ch, start, length);
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if (headerText && "t".equals(qName)) {
                        evidence.headers.add(text.toString());
                        headerText = false;
                    }
                }
            });
        }
        return evidence;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder value = new StringBuilder(64);
        for (byte item : digest.digest()) value.append(String.format("%02x", item & 0xff));
        return value.toString();
    }

    private static class XlsxEvidence {
        private long rows;
        private final List<String> headers = new ArrayList<>();
    }

    private static class CountingReader implements ExportDataReader {
        private final int total;
        private int calls;
        private int maxChunkRows;

        private CountingReader(int total) { this.total = total; }

        @Override
        public ExportChunk fetch(ExportQuerySnapshot snapshot, long lastId, int limit) {
            calls++;
            if (lastId >= total) return new ExportChunk(Collections.emptyList(), lastId);
            int end = (int) Math.min(total, lastId + limit);
            List<ExportRow> rows = new ArrayList<>(end - (int) lastId);
            for (long id = lastId + 1; id <= end; id++) {
                rows.add(new ExportRow(id, Arrays.asList(id, "中文名称" + id,
                        new BigDecimal("19.90"), id % 10 == 0 ? "=SUM(1,2)" : null)));
            }
            maxChunkRows = Math.max(maxChunkRows, rows.size());
            return new ExportChunk(rows, end);
        }
    }
}
