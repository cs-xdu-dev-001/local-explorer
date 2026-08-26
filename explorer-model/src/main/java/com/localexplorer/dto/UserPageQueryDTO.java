package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class UserPageQueryDTO implements Serializable {

    @Min(value = 1, message = "页码不能小于1")
    @Max(value = 100000, message = "页码不能超过100000")
    private int page = 1;

    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private int pageSize = 10;

    /** 用户姓名 */
    @Size(max = 32, message = "用户姓名不能超过32个字符")
    private String name;

    /** 手机号 */
    @Size(max = 32, message = "手机号不能超过32个字符")
    private String phone;
}
