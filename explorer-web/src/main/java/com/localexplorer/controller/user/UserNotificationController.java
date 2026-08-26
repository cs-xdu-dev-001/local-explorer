package com.localexplorer.controller.user;

import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.NotificationPageQueryDTO;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.UserNotificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/user/notification")
@Api(tags = "用户端-通知接口")
public class UserNotificationController {

    @Autowired private UserNotificationService notificationService;

    @GetMapping("/page")
    @ApiOperation("我的通知")
    public Result<PageResult> page(@Valid NotificationPageQueryDTO dto) {
        return Result.success(notificationService.pageQuery(dto, BaseContext.getCurrentId()));
    }

    @GetMapping("/unread-count")
    @ApiOperation("未读通知数量")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.countUnread(BaseContext.getCurrentId()));
    }

    @PutMapping("/{id}/read")
    @ApiOperation("标记通知已读")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @PutMapping("/read-all")
    @ApiOperation("全部标记已读")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(BaseContext.getCurrentId());
        return Result.success();
    }
}
