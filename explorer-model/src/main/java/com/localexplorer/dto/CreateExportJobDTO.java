package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CreateExportJobDTO implements Serializable {

    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "requestId格式不正确")
    private String requestId;

    @NotBlank(message = "导出类型不能为空")
    @Pattern(regexp = "ORDER|USER|REVIEW|OPERATION_LOG", message = "导出类型不正确")
    private String exportType;

    @NotBlank(message = "文件格式不能为空")
    @Pattern(regexp = "CSV|XLSX", message = "文件格式不正确")
    private String fileFormat;

    @Size(max = 128, message = "关键词不能超过128个字符")
    private String keyword;

    @Size(max = 64, message = "预约编号不能超过64个字符")
    private String orderNo;

    @Size(max = 32, message = "联系人不能超过32个字符")
    private String contactName;

    @Size(max = 32, message = "用户姓名不能超过32个字符")
    private String name;

    @Size(max = 32, message = "手机号不能超过32个字符")
    private String phone;

    @Size(max = 128, message = "操作描述不能超过128个字符")
    private String description;

    @Min(value = 0, message = "数据状态不正确")
    @Max(value = 4, message = "数据状态不正确")
    private Integer dataStatus;

    @Min(value = 1, message = "预约类型不正确")
    @Max(value = 2, message = "预约类型不正确")
    private Integer orderType;

    @Positive(message = "用户ID不正确")
    private Long userId;

    @Positive(message = "项目ID不正确")
    private Long itemId;

    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer minRating;

    @Min(value = 1, message = "评分必须在1-5之间")
    @Max(value = 5, message = "评分必须在1-5之间")
    private Integer rating;

    @Pattern(regexp = "replied|unreplied", message = "回复状态不正确")
    private String replyState;

    @Positive(message = "操作人ID不正确")
    private Long operatorId;

    @Pattern(regexp = "GET|POST|PUT|DELETE", message = "请求方法不正确")
    private String requestMethod;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
