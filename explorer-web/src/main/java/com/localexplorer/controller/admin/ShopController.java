package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.result.Result;
import com.localexplorer.service.RuntimeSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 门店营业状态管理（Redis 存储）
 */
@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "门店营业状态接口")
@Slf4j
public class ShopController {

    @Autowired
    private RuntimeSettingService runtimeSettingService;

    /**
     * 设置门店营业状态
     */
    @ApiOperation("设置门店营业状态")
    @PutMapping("/{status}")
    @OperationLog("切换门店营业状态")
    public Result setStatus(@PathVariable Integer status) {
        log.info("设置门店营业状态为：{}", status == 1 ? "营业中" : "休息中");
        runtimeSettingService.setShopStatus(status);
        return Result.success();
    }

    /**
     * 获取门店营业状态
     */
    @GetMapping("/status")
    @ApiOperation("获取门店营业状态")
    public Result<Integer> getStatus() {
        Integer status = runtimeSettingService.getShopStatus();
        log.info("获取门店营业状态为：{}", status == 1 ? "营业中" : "休息中");
        return Result.success(status);
    }
}
