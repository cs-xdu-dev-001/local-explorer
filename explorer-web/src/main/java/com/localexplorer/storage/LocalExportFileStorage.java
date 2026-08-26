package com.localexplorer.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.Set;
import java.time.Instant;
import java.nio.file.DirectoryStream;
import java.util.regex.Pattern;

public class LocalExportFileStorage implements ExportFileStorage {

    private static final Pattern SAFE_STORAGE_KEY = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Path root;
    private final Path tempRoot;
    private final Path filesRoot;

    public LocalExportFileStorage(Path root) {
        try {
            this.root = root.toAbsolutePath().normalize();
            this.tempRoot = this.root.resolve("tmp");
            this.filesRoot = this.root.resolve("files");
            Files.createDirectories(this.tempRoot);
            Files.createDirectories(this.filesRoot);
            rejectSymbolicLink(this.root);
            rejectSymbolicLink(this.tempRoot);
            rejectSymbolicLink(this.filesRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("无法初始化导出目录", ex);
        }
    }

    @Override
    public Path createTempFile(String jobId, String extension) throws IOException {
        validateExtension(extension);
        validateStorageKey(jobId);
        String prefix = "export-" + String.valueOf(jobId) + "-";
        return Files.createTempFile(tempRoot, prefix, "." + extension + ".part");
    }

    @Override
    public StoredExportFile commit(Path tempFile, String jobId, String extension) throws IOException {
        validateExtension(extension);
        validateStorageKey(jobId);
        Path verifiedTemp = verifyInside(tempFile, tempRoot, true);
        String name = "export-" + String.valueOf(jobId) + "-" + compactUuid() + "." + extension;
        Path target = filesRoot.resolve(name).normalize();
        verifyInside(target, filesRoot, false);
        Files.move(verifiedTemp, target, StandardCopyOption.ATOMIC_MOVE);
        return new StoredExportFile(root.relativize(target).toString().replace('\\', '/'),
                Files.size(target), sha256(target));
    }

    @Override
    public Path resolve(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new SecurityException("导出文件路径不合法");
        }
        Path supplied = java.nio.file.Paths.get(relativePath);
        if (supplied.isAbsolute()) {
            throw new SecurityException("禁止使用绝对路径");
        }
        return verifyInside(root.resolve(supplied), root, true);
    }

    @Override
    public StoredExportFile inspect(String relativePath) throws IOException {
        Path file = resolve(relativePath);
        return new StoredExportFile(relativePath, Files.size(file), sha256(file));
    }

    @Override
    public void delete(String relativePath) throws IOException {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }
        Path supplied = java.nio.file.Paths.get(relativePath);
        if (supplied.isAbsolute()) {
            throw new SecurityException("禁止使用绝对路径");
        }
        Path candidate = root.resolve(supplied).normalize();
        verifyLexicalBoundary(candidate, root);
        if (!Files.exists(candidate)) {
            return;
        }
        Files.delete(verifyInside(candidate, root, true));
    }

    @Override
    public int cleanupTemporaryFiles(Instant olderThan) throws IOException {
        return cleanupDirectory(tempRoot, java.util.Collections.<String>emptySet(), olderThan, true);
    }

    @Override
    public int cleanupUnreferencedFiles(Set<String> referencedPaths, Instant olderThan) throws IOException {
        return cleanupDirectory(filesRoot, referencedPaths, olderThan, false);
    }

    private int cleanupDirectory(Path directory, Set<String> referencedPaths, Instant olderThan,
                                 boolean deleteAllCandidates) throws IOException {
        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                Path file = verifyInside(candidate, directory, true);
                if (!Files.isRegularFile(file) || Files.getLastModifiedTime(file).toInstant().isAfter(olderThan)) continue;
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (deleteAllCandidates || !referencedPaths.contains(relative)) {
                    Files.deleteIfExists(file);
                    deleted++;
                }
            }
        }
        return deleted;
    }

    private Path verifyInside(Path candidate, Path expectedRoot, boolean mustExist) throws IOException {
        Path normalized = candidate.toAbsolutePath().normalize();
        Path normalizedRoot = expectedRoot.toAbsolutePath().normalize();
        verifyLexicalBoundary(normalized, normalizedRoot);
        rejectSymbolicSegments(normalizedRoot, normalized);
        if (mustExist) {
            Path realRoot = normalizedRoot.toRealPath();
            Path real = normalized.toRealPath();
            if (!real.startsWith(realRoot)) {
                throw new SecurityException("导出文件路径越界");
            }
            return real;
        }
        return normalized;
    }

    private void verifyLexicalBoundary(Path path, Path boundary) {
        if (!path.startsWith(boundary)) {
            throw new SecurityException("导出文件路径越界");
        }
    }

    private void rejectSymbolicSegments(Path boundary, Path path) throws IOException {
        Path current = boundary;
        rejectSymbolicLink(current);
        Path relative = boundary.relativize(path);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current)) {
                rejectSymbolicLink(current);
            }
        }
    }

    private void rejectSymbolicLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new SecurityException("导出目录不能使用符号链接");
        }
    }

    private void validateExtension(String extension) {
        if (!"csv".equals(extension) && !"xlsx".equals(extension)) {
            throw new IllegalArgumentException("不支持的导出文件格式");
        }
    }

    private void validateStorageKey(String jobId) {
        if (jobId == null || !SAFE_STORAGE_KEY.matcher(jobId).matches()) {
            throw new IllegalArgumentException("导出任务标识不合法");
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest.digest()) {
                value.append(String.format("%02x", item & 0xff));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前JVM不支持SHA-256", ex);
        }
    }

    private String compactUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
