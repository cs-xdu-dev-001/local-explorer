package com.localexplorer.controller.user;

import com.localexplorer.constant.StatusConstant;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.vo.ExploreItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userExploreItemController")
@RequestMapping("/user/explore-item")
@Slf4j
@Api(tags = "用户端-特色项目浏览接口")
public class ExploreItemController {
    @Autowired
    private ExploreItemService itemService;

    /**
     * 根据分类id查询特色项目
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询特色项目")
    public Result<List<ExploreItemVO>> list(Long categoryId) {
        ExploreItem item = new ExploreItem();
        item.setCategoryId(categoryId);
        item.setStatus(StatusConstant.ENABLE);//查询展示中的特色项目

        List<ExploreItemVO> list = itemService.listWithTags(item);

        return Result.success(list);
    }

    @GetMapping("/{id}")
    @ApiOperation("查询特色项目详情")
    public Result<ExploreItemVO> detail(@PathVariable Long id) {
        ExploreItemVO item = itemService.getByIdWithTags(id);
        if (!StatusConstant.ENABLE.equals(item.getStatus())) {
            return Result.error("特色项目已停用");
        }
        return Result.success(item);
    }

}
