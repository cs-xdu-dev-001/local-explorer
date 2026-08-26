package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class UserDTO implements Serializable {

    private Long id;

    @NotBlank(message = "用户姓名不能为空")
    @Size(max = 32, message = "用户姓名不能超过32个字符")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @Pattern(regexp = "^$|^[01]$", message = "性别只能为0或1")
    private String sex;

    @Pattern(regexp = "^$|^\\d{17}[0-9Xx]$", message = "请输入正确的18位身份证号")
    private String idNumber;

    @Size(max = 500, message = "头像地址不能超过500个字符")
    private String avatar;
}
