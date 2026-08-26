package com.localexplorer.controller.user;

import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 用户端 -- 评价
 */
@RestController("userReviewController")
@RequestMapping("/user/review")
@Api(tags = "用户端-评价接口")
@Slf4j
@Validated
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @PostMapping
    @ApiOperation("提交评价")
    public Result save(@Valid @RequestBody ReviewDTO dto) {
        Long userId = BaseContext.getCurrentId();
        reviewService.save(dto, userId);
        return Result.success();
    }

    @GetMapping("/item/{itemId}")
    @ApiOperation("查看项目评价")
    public Result<PageResult> itemReviews(@PathVariable @Min(value = 1, message = "项目ID不正确") Long itemId,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1")
            @Max(value = 100000, message = "页码不能超过100000") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100") Integer pageSize) {
        ReviewPageQueryDTO dto = new ReviewPageQueryDTO();
        dto.setItemId(itemId);
        dto.setPage(page);
        dto.setPageSize(pageSize);
        return Result.success(reviewService.pageQuery(dto));
    }

    @GetMapping("/avg/{itemId}")
    @ApiOperation("项目平均评分")
    public Result avgRating(@PathVariable Long itemId) {
        Double avg = reviewService.avgRating(itemId);
        Long count = reviewService.countByItemId(itemId);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("avg", avg != null ? Math.round(avg * 10) / 10.0 : 0);
        result.put("count", count);
        return Result.success(result);
    }
}
