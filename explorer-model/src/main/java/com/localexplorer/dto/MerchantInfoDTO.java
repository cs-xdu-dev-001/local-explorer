package com.localexplorer.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
public class MerchantInfoDTO implements Serializable {

    @NotBlank(message = "商户名称不能为空")
    @Size(max = 32, message = "商户名称不能超过32个字符")
    private String name;

    @Size(max = 100, message = "展示标语不能超过100个字符")
    private String slogan;

    @NotBlank(message = "联系电话不能为空")
    @Size(max = 32, message = "联系电话不能超过32个字符")
    private String phone;

    @NotBlank(message = "门店地址不能为空")
    @Size(max = 255, message = "门店地址不能超过255个字符")
    private String address;

    @NotBlank(message = "营业时间不能为空")
    @Size(max = 64, message = "营业时间不能超过64个字符")
    private String businessHours;

    @Size(max = 255, message = "预约须知不能超过255个字符")
    private String notice;

    @Size(max = 500, message = "封面图片地址不能超过500个字符")
    private String coverImage;
}
