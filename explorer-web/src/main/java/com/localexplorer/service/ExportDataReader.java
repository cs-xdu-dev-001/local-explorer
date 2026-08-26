package com.localexplorer.service;

import com.localexplorer.domain.ExportQuerySnapshot;

public interface ExportDataReader {
    ExportChunk fetch(ExportQuerySnapshot snapshot, long lastId, int limit);
}
