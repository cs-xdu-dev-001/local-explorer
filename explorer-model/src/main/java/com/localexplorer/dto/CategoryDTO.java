package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class CategoryDTO implements Serializable {

    //主键
    private Long id;

    //类型 1 特色项目分类 2 套餐分类
    @NotNull(message = "分类类型不能为空")
    @Min(value = 1, message = "分类类型只能为1或2")
    @Max(value = 2, message = "分类类型只能为1或2")
    private Integer type;

    //分类名称
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称不能超过32个字符")
    private String name;

    //排序
    @NotNull(message = "分类排序不能为空")
    @Min(value = 0, message = "分类排序不能小于0")
    @Max(value = 9999, message = "分类排序不能超过9999")
    private Integer sort;

}
