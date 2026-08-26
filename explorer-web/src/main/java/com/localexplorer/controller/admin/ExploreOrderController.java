package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.entity.ExploreOrder;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.DashboardService;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.vo.DashboardTrendVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
/**
 * 管理端 -- 预约/订单管理
 */
@RestController("adminExploreOrderController")
@RequestMapping("/admin/explore-order")
@Api(tags = "管理端-预约管理接口")
@Slf4j
public class ExploreOrderController {

    @Autowired
    private ExploreOrderService orderService;
    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/page")
    @ApiOperation("预约分页查询")
    public Result<PageResult> page(@Valid ExploreOrderPageQueryDTO dto) {
        PageResult result = orderService.adminPageQuery(dto);
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据ID查询预约")
    public Result<ExploreOrder> getById(@PathVariable Long id) {
        return Result.success(orderService.getById(id));
    }

    @PutMapping("/status")
    @ApiOperation("更新预约状态")
    @OperationLog("更新预约状态")
    public Result updateStatus(@RequestParam Long id, @RequestParam Integer status) {
        orderService.updateStatus(id, status);
        return Result.success();
    }

    @GetMapping("/trend")
    @ApiOperation("Dashboard 趋势数据")
    public Result<DashboardTrendVO> trend() {
        return Result.success(dashboardService.trend());
    }
}
