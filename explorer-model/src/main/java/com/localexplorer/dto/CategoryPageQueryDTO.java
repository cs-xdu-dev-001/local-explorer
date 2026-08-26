package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class CategoryPageQueryDTO implements Serializable {

    //页码
    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    //每页记录数
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    //分类名称
    @Size(max = 32, message = "分类名称不能超过32个字符")
    private String name;

    //分类类型 1特色项目分类  2套餐分类
    @Min(value = 1, message = "分类类型只能为1或2")
    @Max(value = 2, message = "分类类型只能为1或2")
    private Integer type;

}
