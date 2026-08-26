package com.localexplorer.domain;

public enum ExportType {
    ORDER(false),
    USER(true),
    REVIEW(false),
    OPERATION_LOG(true);

    private final boolean sensitive;

    ExportType(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public boolean isSensitive() {
        return sensitive;
    }
}
