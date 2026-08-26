package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 管理端 -- 评价管理
 */
@RestController("adminReviewController")
@RequestMapping("/admin/review")
@Api(tags = "管理端-评价管理接口")
@Slf4j
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/page")
    @ApiOperation("评价分页查询")
    public Result<PageResult> page(@Valid ReviewPageQueryDTO dto) {
        return Result.success(reviewService.pageQuery(dto));
    }

    @DeleteMapping
    @ApiOperation("批量删除评价")
    @OperationLog("批量删除评价")
    public Result delete(@RequestParam List<Long> ids) {
        reviewService.deleteBatch(ids);
        return Result.success();
    }

    @PutMapping("/reply")
    @ApiOperation("回复评价")
    @OperationLog("回复评价")
    public Result reply(@Valid @RequestBody ReviewDTO dto) {
        reviewService.reply(dto.getId(), dto.getReplyContent());
        return Result.success();
    }
}
