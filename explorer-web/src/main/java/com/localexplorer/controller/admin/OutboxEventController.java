package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.dto.OutboxPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.OrderEventOutboxService;
import com.localexplorer.vo.OutboxStatsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/admin/outbox-event")
@RequireAdmin
@Api(tags = "管理端-可靠事件运维")
public class OutboxEventController {

    @Autowired private OrderEventOutboxService outboxService;

    @GetMapping("/page")
    @ApiOperation("事件分页查询")
    public Result<PageResult> page(@Valid OutboxPageQueryDTO dto) {
        return Result.success(outboxService.pageQuery(dto));
    }

    @GetMapping("/stats")
    @ApiOperation("事件状态统计")
    public Result<OutboxStatsVO> stats() {
        return Result.success(outboxService.stats());
    }

    @PutMapping("/{id}/retry")
    @ApiOperation("重试DEAD事件")
    @OperationLog("重试可靠事件")
    public Result<Void> retry(@PathVariable Long id) {
        outboxService.retryDead(id);
        return Result.success();
    }
}
