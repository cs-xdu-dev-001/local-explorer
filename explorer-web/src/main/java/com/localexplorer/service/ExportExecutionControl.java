package com.localexplorer.service;

public interface ExportExecutionControl {
    void checkpoint(long processedRows);
}
