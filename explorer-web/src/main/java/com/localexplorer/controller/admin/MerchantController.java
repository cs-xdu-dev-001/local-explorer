package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.result.Result;
import com.localexplorer.service.RuntimeSettingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 商家信息管理（Redis Hash 存储）
 */
@RestController
@RequestMapping("/admin/merchant")
@Api(tags = "商家信息相关接口")
@Slf4j
public class MerchantController {

    @Autowired
    private RuntimeSettingService runtimeSettingService;

    @GetMapping("/info")
    @ApiOperation("获取商家信息")
    public Result<MerchantInfoDTO> getInfo() {
        return Result.success(runtimeSettingService.getMerchantInfo());
    }

    @PutMapping("/info")
    @ApiOperation("修改商家信息")
    @OperationLog("修改商户资料")
    public Result updateInfo(@Valid @RequestBody MerchantInfoDTO merchantInfoDTO) {
        log.info("更新商家信息");
        runtimeSettingService.setMerchantInfo(merchantInfoDTO);
        return Result.success();
    }
}
