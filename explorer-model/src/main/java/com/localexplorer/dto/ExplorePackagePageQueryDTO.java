package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class ExplorePackagePageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    @Size(max = 32, message = "套餐名称不能超过32个字符")
    private String name;

    //分类id
    @Positive(message = "套餐分类不正确")
    private Integer categoryId;

    //状态 0表示禁用 1表示启用
    @Min(value = 0, message = "状态参数只能为0或1")
    @Max(value = 1, message = "状态参数只能为0或1")
    private Integer status;

}
