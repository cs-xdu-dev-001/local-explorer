package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.dto.ExplorePackagePageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExplorePackageService;
import com.localexplorer.vo.ExplorePackageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 探店套餐管理
 */
@RestController
@RequestMapping("/admin/explore-package")
@Api(tags = "探店套餐相关接口")
@Slf4j
public class ExplorePackageController {

    @Autowired
    private ExplorePackageService packageService;

    @PostMapping
    @ApiOperation("新增探店套餐")
    @OperationLog("新增探店套餐")
    public Result save(@Valid @RequestBody ExplorePackageDTO packageDTO) {
        log.info("新增探店套餐: {}", packageDTO.getName());
        packageService.saveWithItems(packageDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("探店套餐分页查询")
    public Result<PageResult> page(@Valid ExplorePackagePageQueryDTO packagePageQueryDTO) {
        PageResult pageResult = packageService.pageQuery(packagePageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("批量删除探店套餐")
    @OperationLog("批量删除探店套餐")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("批量删除探店套餐: {}", ids);
        packageService.deleteBatch(ids);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询探店套餐")
    public Result<ExplorePackageVO> getById(@PathVariable Long id) {
        ExplorePackageVO packageVO = packageService.getByIdWithItems(id);
        return Result.success(packageVO);
    }

    @PutMapping
    @ApiOperation("修改探店套餐")
    @OperationLog("修改探店套餐")
    public Result update(@Valid @RequestBody ExplorePackageDTO packageDTO) {
        log.info("修改探店套餐: {}", packageDTO.getName());
        packageService.update(packageDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("探店套餐上架下架")
    @OperationLog("探店套餐上下架")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("探店套餐上下架: status={}, id={}", status, id);
        packageService.startOrStop(status, id);
        return Result.success();
    }
}
