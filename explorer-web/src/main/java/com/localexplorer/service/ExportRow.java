package com.localexplorer.service;

import java.util.List;

public class ExportRow {
    private final long id;
    private final List<Object> cells;

    public ExportRow(long id, List<Object> cells) {
        this.id = id;
        this.cells = cells;
    }

    public long getId() { return id; }
    public List<Object> getCells() { return cells; }
}
