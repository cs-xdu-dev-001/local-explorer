package com.localexplorer.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ExportJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    EXPIRED;

    public boolean canTransitionTo(ExportJobStatus target) {
        if (target == null || target == this) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == RUNNING || target == CANCELED;
            case RUNNING:
                return target == PENDING || target == SUCCEEDED || target == FAILED || target == CANCELED;
            case SUCCEEDED:
                return target == EXPIRED;
            default:
                return false;
        }
    }

    public boolean isDownloadable() {
        return this == SUCCEEDED;
    }

    public boolean isTerminal() {
        Set<ExportJobStatus> terminal = EnumSet.of(SUCCEEDED, FAILED, CANCELED, EXPIRED);
        return terminal.contains(this);
    }
}
