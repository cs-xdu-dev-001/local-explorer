package com.localexplorer.storage;

import java.nio.file.Path;

public class ExportDownload {
    private final Path file;
    private final String fileName;
    private final String contentType;
    private final long size;

    public ExportDownload(Path file, String fileName, String contentType, long size) {
        this.file = file;
        this.fileName = fileName;
        this.contentType = contentType;
        this.size = size;
    }

    public Path getFile() { return file; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
}
