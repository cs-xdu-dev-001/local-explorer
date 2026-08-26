package com.localexplorer.controller.admin;

import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.dto.OperationLogPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.OperationLogService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 管理端 -- 操作日志
 */
@RestController
@RequestMapping("/admin/operation-log")
@Api(tags = "管理端-操作日志接口")
@Slf4j
@RequireAdmin
public class OperationLogController {

    @Autowired
    private OperationLogService logService;

    @GetMapping("/page")
    @ApiOperation("操作日志分页查询")
    public Result<PageResult> page(@Valid OperationLogPageQueryDTO dto) {
        return Result.success(logService.pageQuery(dto));
    }
}
