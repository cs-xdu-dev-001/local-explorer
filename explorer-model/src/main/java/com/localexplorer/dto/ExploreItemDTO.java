package com.localexplorer.dto;

import com.localexplorer.entity.ExploreItemTag;
import lombok.Data;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ExploreItemDTO implements Serializable {

    private Long id;
    //特色项目名称
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 32, message = "项目名称不能超过32个字符")
    private String name;
    //特色项目分类id
    @NotNull(message = "项目分类不能为空")
    @Positive(message = "项目分类不正确")
    private Long categoryId;
    //特色项目价格
    @NotNull(message = "项目价格不能为空")
    @DecimalMin(value = "0.01", message = "项目价格必须大于0")
    @Digits(integer = 8, fraction = 2, message = "项目价格最多8位整数和2位小数")
    private BigDecimal price;
    //图片
    @Size(max = 255, message = "项目图片地址不能超过255个字符")
    private String image;
    //描述信息
    @Size(max = 255, message = "项目描述不能超过255个字符")
    private String description;
    @NotNull(message = "项目时长不能为空")
    @Min(value = 1, message = "项目时长必须大于0")
    @Max(value = 10080, message = "项目时长不能超过10080分钟")
    private Integer durationMinutes;
    @NotNull(message = "可预约容量不能为空")
    @Min(value = 1, message = "可预约容量必须大于0")
    @Max(value = 100000, message = "可预约容量不能超过100000")
    private Integer capacity;
    private Integer booked;
    @Size(max = 64, message = "商圈不能超过64个字符")
    private String district;
    @Size(max = 255, message = "详细地址不能超过255个字符")
    private String address;
    @Size(max = 255, message = "集合点不能超过255个字符")
    private String meetingPoint;
    @Size(max = 255, message = "取消规则不能超过255个字符")
    private String cancelPolicy;
    //0 停售 1 起售
    @NotNull(message = "项目状态不能为空")
    @Min(value = 0, message = "项目状态只能为0或1")
    @Max(value = 1, message = "项目状态只能为0或1")
    private Integer status;
    //标签
    @Size(max = 20, message = "项目标签不能超过20个")
    private List<ExploreItemTag> tags = new ArrayList<>();

}
