package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;

@Data
public class OutboxPageQueryDTO {

    @Min(value = 1, message = "页码必须大于0")
    private int page = 1;

    @Min(value = 1, message = "每页数量必须大于0")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 20;

    @Pattern(regexp = "PENDING|PROCESSING|PROCESSED|DEAD", message = "事件状态不合法")
    private String status;

    private String eventType;
}
