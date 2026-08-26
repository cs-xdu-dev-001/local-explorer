package com.localexplorer.controller.user;

import com.localexplorer.entity.Category;
import com.localexplorer.result.Result;
import com.localexplorer.service.CategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
@Api(tags = "用户端-内容分类接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询内容分类
     * @param type
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("查询内容分类")
    public Result<List<Category>> list(Integer type) {
        List<Category> list = categoryService.list(type);
        return Result.success(list);
    }
}
