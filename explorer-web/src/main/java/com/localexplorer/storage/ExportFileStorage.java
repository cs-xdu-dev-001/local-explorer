package com.localexplorer.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;

public interface ExportFileStorage {

    Path createTempFile(String jobId, String extension) throws IOException;

    StoredExportFile commit(Path tempFile, String jobId, String extension) throws IOException;

    Path resolve(String relativePath) throws IOException;

    StoredExportFile inspect(String relativePath) throws IOException;

    void delete(String relativePath) throws IOException;

    int cleanupTemporaryFiles(Instant olderThan) throws IOException;

    int cleanupUnreferencedFiles(Set<String> referencedPaths, Instant olderThan) throws IOException;
}
