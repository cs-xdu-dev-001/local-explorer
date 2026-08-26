package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class OperationLogPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    /** 操作描述关键词 */
    @Size(max = 128, message = "操作描述不能超过128个字符")
    private String description;

    /** 综合关键词：操作描述、操作人、路径或IP */
    @Size(max = 128, message = "操作日志关键词不能超过128个字符")
    private String keyword;

    /** 操作人ID */
    @Positive(message = "操作人ID不正确")
    private Long operatorId;

    /** 请求方法 */
    @Pattern(regexp = "GET|POST|PUT|DELETE", message = "请求方法不正确")
    private String requestMethod;
}
