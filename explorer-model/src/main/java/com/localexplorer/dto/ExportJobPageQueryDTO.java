package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ExportJobPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    @Pattern(regexp = "ORDER|USER|REVIEW|OPERATION_LOG", message = "导出类型不正确")
    private String exportType;

    @Pattern(regexp = "CSV|XLSX", message = "文件格式不正确")
    private String fileFormat;

    @Pattern(regexp = "PENDING|RUNNING|SUCCEEDED|FAILED|CANCELED|EXPIRED", message = "任务状态不正确")
    private String status;

    @Positive(message = "操作人ID不正确")
    private Long operatorId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
