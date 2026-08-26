package com.localexplorer.controller.user;

import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.result.Result;
import com.localexplorer.service.RuntimeSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端商家信息（只读）
 */
@RestController("userMerchantController")
@RequestMapping("/user/merchant")
@Api(tags = "用户端-商家信息接口")
@Slf4j
public class MerchantController {

    @Autowired
    private RuntimeSettingService runtimeSettingService;

    @GetMapping("/info")
    @ApiOperation("获取商家信息")
    public Result<MerchantInfoDTO> getInfo() {
        return Result.success(runtimeSettingService.getMerchantInfo());
    }
}
