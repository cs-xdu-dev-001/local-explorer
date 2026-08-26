package com.localexplorer.controller.admin;

import com.localexplorer.annotation.OperationLog;
import com.localexplorer.annotation.RequireAdmin;
import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.EmployeeDTO;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.dto.EmployeePageQueryDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.result.PageResult;
import com.localexplorer.result.Result;
import com.localexplorer.service.EmployeeService;
import com.localexplorer.service.AuthenticationResult;
import com.localexplorer.service.AuthenticationService;
import com.localexplorer.service.AuthRequestSecurity;
import com.localexplorer.service.impl.AuthSessionServiceImpl;
import com.localexplorer.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "商家员工相关接口")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthRequestSecurity authRequestSecurity;

    /**
     * 登录
     */
    @PostMapping("/login")
    @ApiOperation(value = "商家员工登录")
    public Result<EmployeeLoginVO> login(@Valid @RequestBody EmployeeLoginDTO employeeLoginDTO,
                                        HttpServletRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticationService.loginEmployee(employeeLoginDTO, request);
        authRequestSecurity.writeRefreshCookie(response, AuthSessionServiceImpl.EMPLOYEE, result.getRefreshToken());
        EmployeeLoginVO employeeLoginVO = toLoginVO(result);

        return Result.success(employeeLoginVO);
    }

    @PostMapping("/refresh")
    @ApiOperation("轮换员工刷新凭证")
    public Result<EmployeeLoginVO> refresh(HttpServletRequest request, HttpServletResponse response) {
        AuthenticationResult result = authenticationService.refresh(AuthSessionServiceImpl.EMPLOYEE, request);
        authRequestSecurity.writeRefreshCookie(response, AuthSessionServiceImpl.EMPLOYEE, result.getRefreshToken());
        return Result.success(toLoginVO(result));
    }

    /**
     * 退出
     */
    @PostMapping("/logout")
    @ApiOperation("商家员工退出")
    public Result<String> logout(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logout(AuthSessionServiceImpl.EMPLOYEE, request);
        authRequestSecurity.clearRefreshCookie(response, AuthSessionServiceImpl.EMPLOYEE);
        return Result.success();
    }

    @PostMapping("/logout-all")
    @ApiOperation("退出员工账号全部会话")
    public Result<String> logoutAll(HttpServletRequest request, HttpServletResponse response) {
        authenticationService.logoutAll(AuthSessionServiceImpl.EMPLOYEE, BaseContext.getCurrentId(), request);
        authRequestSecurity.clearRefreshCookie(response, AuthSessionServiceImpl.EMPLOYEE);
        return Result.success();
    }

    private EmployeeLoginVO toLoginVO(AuthenticationResult result) {
        return EmployeeLoginVO.builder().id(result.getId()).userName(result.getUserName())
                .name(result.getName()).role(result.getRole()).token(result.getAccessToken())
                .accessExpiresInMillis(result.getAccessExpiresInMillis()).build();
    }

    /**
     * 新增员工
     */
    @PostMapping
    @RequireAdmin
    @ApiOperation("新增商家员工")
    @OperationLog("新增员工")
    public Result save(@Valid @RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工: {}", employeeDTO.getUsername());
        employeeService.save(employeeDTO);
        return Result.success();
    }

    /**
     * 员工分页查询
     */
    @GetMapping("/page")
    @RequireAdmin
    @ApiOperation("商家员工分页查询")
    public Result<PageResult> page(@Valid EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("员工分页查询，参数为：{}", employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用禁用员工账号
     */
    @PostMapping("/status/{status}")
    @RequireAdmin
    @ApiOperation("启用禁用商家员工账号")
    @OperationLog("员工账号启停")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("启用禁用员工账号：status={}, id={}", status, id);
        employeeService.startOrStop(status, id);
        return Result.success();
    }

    /**
     * 根据id查询员工信息
     */
    @GetMapping("/{id}")
    @RequireAdmin
    @ApiOperation("根据id查询员工信息")
    public Result<Employee> getById(@PathVariable Long id) {
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     */
    @PutMapping
    @RequireAdmin
    @ApiOperation("编辑员工信息")
    @OperationLog("修改员工资料")
    public Result update(@Valid @RequestBody EmployeeDTO employeeDTO) {
        log.info("编辑员工信息：{}", employeeDTO.getUsername());
        employeeService.update(employeeDTO);
        return Result.success();
    }

    /**
     * 删除员工账号
     */
    @DeleteMapping
    @RequireAdmin
    @ApiOperation("删除员工账号")
    @OperationLog("删除员工")
    public Result delete(Long id) {
        log.info("删除员工账号：{}", id);
        employeeService.deleteById(id);
        return Result.success();
    }
}
