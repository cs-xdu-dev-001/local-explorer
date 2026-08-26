package com.localexplorer.controller.user;

import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.ExploreOrderDTO;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExploreOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 用户端 -- 预约/下单
 */
@RestController("userExploreOrderController")
@RequestMapping("/user/explore-order")
@Api(tags = "用户端-预约接口")
@Slf4j
public class ExploreOrderController {

    @Autowired
    private ExploreOrderService orderService;

    @PostMapping
    @ApiOperation("创建预约")
    public Result<Long> create(@Valid @RequestBody ExploreOrderDTO dto) {
        Long userId = BaseContext.getCurrentId();
        Long orderId = orderService.create(dto, userId);
        return Result.success(orderId);
    }

    @GetMapping("/page")
    @ApiOperation("我的预约列表")
    public Result<PageResult> page(@Valid ExploreOrderPageQueryDTO dto) {
        Long userId = BaseContext.getCurrentId();
        dto.setUserId(userId);
        return Result.success(orderService.pageQuery(dto));
    }

    @GetMapping("/{id}")
    @ApiOperation("预约详情")
    public Result getById(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(orderService.getByIdForUser(id, userId));
    }

    @PutMapping("/{id}/cancel")
    @ApiOperation("取消本人预约")
    public Result cancel(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        orderService.cancelByUser(id, userId);
        return Result.success();
    }
}
