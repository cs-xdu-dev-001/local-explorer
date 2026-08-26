package com.localexplorer.service;

import com.localexplorer.domain.ExportQuerySnapshot;

public class ExportSnapshotPlan {

    private final ExportQuerySnapshot snapshot;
    private final long totalRows;

    public ExportSnapshotPlan(ExportQuerySnapshot snapshot, long totalRows) {
        this.snapshot = snapshot;
        this.totalRows = totalRows;
    }

    public ExportQuerySnapshot getSnapshot() {
        return snapshot;
    }

    public long getTotalRows() {
        return totalRows;
    }
}
