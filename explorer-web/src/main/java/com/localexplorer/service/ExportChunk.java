package com.localexplorer.service;

import java.util.List;

public class ExportChunk {
    private final List<ExportRow> rows;
    private final long lastId;

    public ExportChunk(List<ExportRow> rows, long lastId) {
        this.rows = rows;
        this.lastId = lastId;
    }

    public List<ExportRow> getRows() { return rows; }
    public long getLastId() { return lastId; }
}
