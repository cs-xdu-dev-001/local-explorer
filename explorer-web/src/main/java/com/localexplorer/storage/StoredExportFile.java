package com.localexplorer.storage;

public class StoredExportFile {

    private final String relativePath;
    private final long size;
    private final String checksum;

    public StoredExportFile(String relativePath, long size, String checksum) {
        this.relativePath = relativePath;
        this.size = size;
        this.checksum = checksum;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getSize() {
        return size;
    }

    public String getChecksum() {
        return checksum;
    }
}
