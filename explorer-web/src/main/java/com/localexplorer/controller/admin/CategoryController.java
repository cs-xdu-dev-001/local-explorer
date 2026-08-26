package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.dto.CategoryDTO;
import com.localexplorer.dto.CategoryPageQueryDTO;
import com.localexplorer.entity.Category;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 分类管理
 */
@RestController
@RequestMapping("/admin/category")
@Api(tags = "内容分类相关接口")
@Slf4j
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    @ApiOperation("新增内容分类")
    @OperationLog("新增内容分类")
    public Result<String> save(@Valid @RequestBody CategoryDTO categoryDTO) {
        log.info("新增内容分类：{}", categoryDTO.getName());
        categoryService.save(categoryDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("内容分类分页查询")
    public Result<PageResult> page(@Valid CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分页查询：page={}, pageSize={}", categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());
        PageResult pageResult = categoryService.pageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping
    @ApiOperation("删除内容分类")
    @OperationLog("删除内容分类")
    public Result<String> deleteById(Long id) {
        log.info("删除内容分类：{}", id);
        categoryService.deleteById(id);
        return Result.success();
    }

    @PutMapping
    @ApiOperation("修改内容分类")
    @OperationLog("修改内容分类")
    public Result<String> update(@Valid @RequestBody CategoryDTO categoryDTO) {
        log.info("修改内容分类：{}", categoryDTO.getName());
        categoryService.update(categoryDTO);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用内容分类")
    @OperationLog("内容分类上下架")
    public Result<String> startOrStop(@PathVariable("status") Integer status, Long id) {
        log.info("启用禁用内容分类：status={}, id={}", status, id);
        categoryService.startOrStop(status, id);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("根据类型查询内容分类")
    public Result<List<Category>> list(Integer type) {
        List<Category> list = categoryService.list(type);
        return Result.success(list);
    }
}
