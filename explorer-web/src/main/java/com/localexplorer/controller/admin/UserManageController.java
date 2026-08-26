package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.dto.UserPageQueryDTO;
import com.localexplorer.entity.User;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.service.UserInteractionService;
import com.localexplorer.service.UserService;
import com.localexplorer.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;

import javax.validation.Valid;

/**
 * 管理端 -- 用户管理
 */
@RestController("adminUserManageController")
@RequestMapping("/admin/user-manage")
@Api(tags = "管理端-用户管理接口")
@Slf4j
@RequireAdmin
public class UserManageController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ExploreOrderService orderService;

    @Autowired
    private UserInteractionService interactionService;

    @Autowired
    private UserService userService;

    @GetMapping("/page")
    @ApiOperation("用户分页查询")
    public Result<PageResult> page(@Valid UserPageQueryDTO dto) {
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<UserVO> page = userMapper.pageQuery(dto);
        // 填充统计信息
        for (UserVO user : page.getResult()) {
            user.setOrderCount(orderService.countByUserId(user.getId()));
            user.setBrowseCount(interactionService.getBrowseCount(user.getId()));
            user.setFavoriteCount(interactionService.getFavoriteCount(user.getId()));
        }
        return Result.success(new PageResult(page.getTotal(), page.getResult()));
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询用户信息")
    public Result<User> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PutMapping("/{id}")
    @ApiOperation("编辑用户信息")
    @OperationLog("修改用户资料")
    public Result update(@PathVariable Long id, @Valid @RequestBody UserDTO userDTO) {
        userDTO.setId(id);
        userService.update(userDTO);
        return Result.success();
    }

    @PutMapping("/{id}/password/reset")
    @ApiOperation("重置用户密码")
    @OperationLog("重置用户密码")
    public Result resetPassword(@PathVariable Long id) {
        log.info("重置用户密码：{}", id);
        userService.resetPassword(id);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用用户账号")
    @OperationLog("用户账号启停")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("启用禁用用户账号：status={}, id={}", status, id);
        userService.startOrStop(status, id);
        return Result.success();
    }
}
