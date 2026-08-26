package com.localexplorer.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalExportFileStorageTest {

    @TempDir
    Path root;

    @Test
    void commitsWithAtomicMoveAndReturnsVerifiedMetadata() throws Exception {
        LocalExportFileStorage storage = new LocalExportFileStorage(root);
        Path temp = storage.createTempFile("12", "csv");
        Files.write(temp, "name\n林夏\n".getBytes(StandardCharsets.UTF_8));

        StoredExportFile stored = storage.commit(temp, "12", "csv");

        assertThat(stored.getRelativePath()).doesNotContain("..").endsWith(".csv");
        assertThat(stored.getSize()).isEqualTo(Files.size(storage.resolve(stored.getRelativePath())));
        assertThat(stored.getChecksum()).matches("[0-9a-f]{64}");
        assertThat(Files.exists(temp)).isFalse();
    }

    @Test
    void rejectsTraversalAbsolutePathsAndUnexpectedExtensions() {
        LocalExportFileStorage storage = new LocalExportFileStorage(root);

        assertThatThrownBy(() -> storage.resolve("../outside.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.resolve(root.resolve("outside.txt").toString()))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> storage.createTempFile("1", "../../cmd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.createTempFile("../outside", "csv"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        LocalExportFileStorage storage = new LocalExportFileStorage(root);
        Path temp = storage.createTempFile("9", "csv");
        Files.write(temp, new byte[]{1});
        StoredExportFile stored = storage.commit(temp, "9", "csv");

        storage.delete(stored.getRelativePath());
        storage.delete(stored.getRelativePath());

        assertThat(Files.exists(root.resolve(stored.getRelativePath()))).isFalse();
    }

    @Test
    void rejectsSymbolicLinkEscape() throws Exception {
        LocalExportFileStorage storage = new LocalExportFileStorage(root);
        Path outsideDirectory = Files.createTempDirectory("export-secret");
        Path outside = Files.write(outsideDirectory.resolve("secret.csv"), new byte[]{1});
        Path link = root.resolve("files").resolve("escaped");
        try {
            Files.createSymbolicLink(link, outsideDirectory);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J",
                    link.toString(), outsideDirectory.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor()).isZero();
        }
        try {
            assertThatThrownBy(() -> storage.resolve("files/escaped/secret.csv"))
                    .isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(outside);
            Files.deleteIfExists(outsideDirectory);
        }
    }
}
