package com.localexplorer.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportJobVO implements Serializable {

    private String jobId;
    private String requestId;
    private String exportType;
    private String fileFormat;
    private String status;
    private Integer progress;
    private Long totalRows;
    private Long processedRows;
    private String fileName;
    private Long fileSize;
    private String checksum;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
