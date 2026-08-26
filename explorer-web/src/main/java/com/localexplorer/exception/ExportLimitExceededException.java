package com.localexplorer.exception;

public class ExportLimitExceededException extends java.io.IOException {

    public static final String FILE_TOO_LARGE = "EXPORT_FILE_TOO_LARGE";
    public static final String RUNTIME_EXCEEDED = "EXPORT_RUNTIME_EXCEEDED";

    private final String errorCode;

    public ExportLimitExceededException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
