package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

@Data
public class LoginGuardPageQueryDTO {
    @Min(value = 1, message = "页码不能小于1")
    private int page = 1;
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private int pageSize = 20;
    private String principalType;
}
