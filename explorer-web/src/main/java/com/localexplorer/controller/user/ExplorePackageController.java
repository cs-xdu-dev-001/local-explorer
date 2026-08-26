package com.localexplorer.controller.user;

import com.localexplorer.constant.StatusConstant;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExplorePackageService;
import com.localexplorer.vo.PackageItemVO;
import com.localexplorer.vo.ExplorePackageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userExplorePackageController")
@RequestMapping("/user/explore-package")
@Api(tags = "用户端-探店套餐浏览接口")
public class ExplorePackageController {

    @Autowired
    private ExplorePackageService packageService;

    @GetMapping("/list")
    @ApiOperation("根据分类id查询探店套餐")
    public Result<List<ExplorePackage>> list(Long categoryId) {
        ExplorePackage packageEntity = new ExplorePackage();
        packageEntity.setCategoryId(categoryId);
        packageEntity.setStatus(StatusConstant.ENABLE);
        List<ExplorePackage> list = packageService.list(packageEntity);
        return Result.success(list);
    }

    @GetMapping("/items/{id}")
    @ApiOperation("根据套餐id查询包含的特色项目列表")
    public Result<List<PackageItemVO>> packageItems(@PathVariable Long id) {
        List<PackageItemVO> list = packageService.getPackageItemsById(id);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询探店套餐详情")
    public Result<ExplorePackageVO> detail(@PathVariable Long id) {
        ExplorePackageVO packageInfo = packageService.getByIdWithItems(id);
        if (!StatusConstant.ENABLE.equals(packageInfo.getStatus())) {
            return Result.error("探店套餐已停用");
        }
        return Result.success(packageInfo);
    }
}
