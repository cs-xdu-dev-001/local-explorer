package com.localexplorer.entity;

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
public class ExportJob implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private String requestId;
    private String exportType;
    private String fileFormat;
    private String querySnapshot;
    private String status;
    private Integer progress;
    private Long totalRows;
    private Long processedRows;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String checksum;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private String errorCode;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 仅供执行器标记本次领取是否来自过期租约，不持久化。 */
    private Boolean recovered;
}
