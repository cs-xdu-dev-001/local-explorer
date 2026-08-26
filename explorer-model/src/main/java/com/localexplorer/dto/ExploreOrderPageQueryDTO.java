package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class ExploreOrderPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    /** 通用关键词：预约编号、内容、用户、联系人、手机号 */
    @Size(max = 100, message = "关键词不能超过100个字符")
    private String keyword;

    /** 预约编号 */
    @Size(max = 64, message = "预约编号不能超过64个字符")
    private String orderNo;

    /** 联系人 */
    @Size(max = 32, message = "联系人不能超过32个字符")
    private String contactName;

    /** 状态 0=待确认 1=已确认 2=已完成 3=已取消 4=超时取消 */
    @Min(value = 0, message = "预约状态不正确")
    @Max(value = 4, message = "预约状态不正确")
    private Integer status;

    /** 订单类型 1=项目 2=套餐 */
    @Min(value = 1, message = "预约类型不正确")
    @Max(value = 2, message = "预约类型不正确")
    private Integer orderType;

    /** 用户ID（管理端按用户筛选） */
    @Positive(message = "用户ID不正确")
    private Long userId;
}
