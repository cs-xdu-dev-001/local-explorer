package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.dto.ExploreItemPageQueryDTO;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.vo.ExploreItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/admin/explore-item")
@Api(tags = "Explore item APIs")
@Slf4j
public class ExploreItemController {

    @Autowired
    private ExploreItemService itemService;

    @PostMapping
    @ApiOperation("Create explore item")
    @OperationLog("新增特色项目")
    public Result save(@Valid @RequestBody ExploreItemDTO itemDTO) {
        log.info("Create explore item: {}", itemDTO.getName());
        itemService.saveWithTags(itemDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("Page explore items")
    public Result<PageResult> page(@Valid ExploreItemPageQueryDTO itemPageQueryDTO) {
        log.info("Page explore items: page={}, pageSize={}", itemPageQueryDTO.getPage(), itemPageQueryDTO.getPageSize());
        PageResult pageResult = itemService.pageQuery(itemPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("Delete explore items")
    @OperationLog("删除特色项目")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("Delete explore items: {}", ids);
        itemService.deleteBatch(ids);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("Get explore item by id")
    public Result<ExploreItemVO> getById(@PathVariable Long id) {
        ExploreItemVO itemVO = itemService.getByIdWithTags(id);
        return Result.success(itemVO);
    }

    @PutMapping
    @ApiOperation("Update explore item")
    @OperationLog("修改特色项目")
    public Result update(@Valid @RequestBody ExploreItemDTO itemDTO) {
        log.info("Update explore item: {}", itemDTO.getName());
        itemService.updateWithTags(itemDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("Update explore item status")
    @OperationLog("特色项目上下架")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("Update explore item status: status={}, id={}", status, id);
        itemService.startOrStop(status, id);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("List explore items by category")
    public Result<List<ExploreItem>> list(Long categoryId) {
        List<ExploreItem> list = itemService.list(categoryId);
        return Result.success(list);
    }
}
