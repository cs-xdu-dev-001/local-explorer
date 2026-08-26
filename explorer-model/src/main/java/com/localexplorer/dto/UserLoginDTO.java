package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "用户密码不能为空")
    private String password;
}
