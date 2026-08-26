package com.localexplorer.controller.user;

import com.localexplorer.context.BaseContext;
import com.localexplorer.result.Result;
import com.localexplorer.service.UserInteractionService;
import com.localexplorer.vo.ExploreItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * 用户端 -- 收藏与浏览记录
 *
 * <p>使用 Redis ZSet 存储，score 为时间戳，天然按时间倒序。</p>
 */
@RestController("userFavoriteController")
@RequestMapping("/user/favorite")
@Api(tags = "用户端-收藏与浏览记录接口")
@Slf4j
@Validated
public class FavoriteController {

    @Autowired
    private UserInteractionService interactionService;

    /**
     * 添加浏览记录（用户点击进入项目详情时调用）
     */
    @PostMapping("/browse/{itemId}")
    @ApiOperation("添加浏览记录")
    public Result addBrowseRecord(@PathVariable @Min(value = 1, message = "项目ID不正确") Long itemId) {
        Long userId = BaseContext.getCurrentId();
        interactionService.addBrowseRecord(userId, itemId);
        return Result.success();
    }

    /**
     * 获取浏览记录（分页，按时间倒序）
     */
    @GetMapping("/browse")
    @ApiOperation("获取浏览记录")
    public Result<List<ExploreItemVO>> getBrowseHistory(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1")
            @Max(value = 100000, message = "页码不能超过100000") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        List<ExploreItemVO> list = interactionService.getBrowseHistory(userId, page, pageSize);
        return Result.success(list);
    }

    /**
     * 收藏项目
     */
    @PostMapping("/{itemId}")
    @ApiOperation("收藏项目")
    public Result addFavorite(@PathVariable @Min(value = 1, message = "项目ID不正确") Long itemId) {
        Long userId = BaseContext.getCurrentId();
        interactionService.addFavorite(userId, itemId);
        return Result.success();
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/{itemId}")
    @ApiOperation("取消收藏")
    public Result removeFavorite(@PathVariable @Min(value = 1, message = "项目ID不正确") Long itemId) {
        Long userId = BaseContext.getCurrentId();
        interactionService.removeFavorite(userId, itemId);
        return Result.success();
    }

    /**
     * 获取收藏列表（分页，按时间倒序）
     */
    @GetMapping
    @ApiOperation("获取收藏列表")
    public Result<List<ExploreItemVO>> getFavorites(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于1")
            @Max(value = 100000, message = "页码不能超过100000") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量不能小于1")
            @Max(value = 100, message = "每页数量不能超过100") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        List<ExploreItemVO> list = interactionService.getFavorites(userId, page, pageSize);
        return Result.success(list);
    }

    /**
     * 查询项目是否已被当前用户收藏
     */
    @GetMapping("/check/{itemId}")
    @ApiOperation("检查是否已收藏")
    public Result<Boolean> isFavorited(@PathVariable @Min(value = 1, message = "项目ID不正确") Long itemId) {
        Long userId = BaseContext.getCurrentId();
        boolean result = interactionService.isFavorited(userId, itemId);
        return Result.success(result);
    }

    /**
     * 获取浏览记录总数
     */
    @GetMapping("/browse/count")
    @ApiOperation("浏览记录总数")
    public Result<Long> getBrowseCount() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(interactionService.getBrowseCount(userId));
    }

    /**
     * 获取收藏总数
     */
    @GetMapping("/count")
    @ApiOperation("收藏总数")
    public Result<Long> getFavoriteCount() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(interactionService.getFavoriteCount(userId));
    }
}
