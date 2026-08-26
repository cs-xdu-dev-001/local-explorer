package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Future;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预约/订单 请求参数
 */
@Data
public class ExploreOrderDTO implements Serializable {

    private Long id;

    /** 1=特色项目 2=探店套餐 */
    @NotNull(message = "预约类型不能为空")
    @Min(value = 1, message = "预约类型不正确")
    @Max(value = 2, message = "预约类型不正确")
    private Integer orderType;

    /** 特色项目ID */
    private Long itemId;

    /** 探店套餐ID */
    private Long packageId;

    /** 项目/套餐名称 */
    private String itemName;

    /** 预约金额 */
    private BigDecimal amount;

    /** 预约人数 */
    @Min(value = 1, message = "预约人数必须大于0")
    private Integer peopleCount;

    /** 联系人 */
    @NotBlank(message = "联系人不能为空")
    @Size(max = 32, message = "联系人不能超过32个字符")
    private String contactName;

    /** 联系电话 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    private String contactPhone;

    /** 预约时间 */
    @NotNull(message = "预约时间不能为空")
    @Future(message = "预约时间必须晚于当前时间")
    private LocalDateTime reserveTime;

    /** 客户端幂等请求ID，同一用户重复提交同一requestId时返回同一预约 */
    @Size(max = 64, message = "幂等请求ID不能超过64个字符")
    private String requestId;

    /** 备注 */
    @Size(max = 255, message = "备注不能超过255个字符")
    private String remark;

    /** 状态（管理端操作用） */
    private Integer status;
}
