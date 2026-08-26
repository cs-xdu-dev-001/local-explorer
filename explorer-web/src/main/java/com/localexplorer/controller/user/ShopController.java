package com.localexplorer.controller.user;

import com.localexplorer.result.Result;
import com.localexplorer.service.RuntimeSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户端门店状态（只读）
 */
@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "用户端-门店状态接口")
@Slf4j
public class ShopController {

    @Autowired
    private RuntimeSettingService runtimeSettingService;

    @GetMapping("/status")
    @ApiOperation("获取门店营业状态")
    public Result<Integer> getStatus() {
        Integer status = runtimeSettingService.getShopStatus();
        log.info("获取门店营业状态为：{}", status == 1 ? "营业中" : "休息中");
        return Result.success(status);
    }
}
