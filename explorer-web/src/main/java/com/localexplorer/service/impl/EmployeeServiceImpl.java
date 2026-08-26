package com.localexplorer.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.PasswordConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.EmployeeDTO;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.dto.EmployeePageQueryDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.exception.AccountLockedException;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.DeletionNotAllowedException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.mapper.OperationLogMapper;
import com.localexplorer.result.PageResult;
import com.localexplorer.service.EmployeeService;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.AuthSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private AdminPermissionService adminPermissionService;
    @Autowired
    private AuthSessionService authSessionService;

    /**
     * 员工登录
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        Employee employee = employeeMapper.getByUsername(username);

        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!password.equals(employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        return employee;
    }

    /**
     * 新增员工，密码使用 MD5 默认密码
     */
    public void save(EmployeeDTO employeeDTO) {
        adminPermissionService.requireAdmin();
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);

        employee.setStatus(StatusConstant.ENABLE);
        if (employee.getRole() == null || employee.getRole().trim().isEmpty()) {
            employee.setRole(AdminPermissionService.ROLE_STAFF);
        }
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));

        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     */
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 启用禁用员工账号
     */
    @Transactional
    public void startOrStop(Integer status, Long id) {
        adminPermissionService.requireAdmin();
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        if (Long.valueOf(1L).equals(id) && StatusConstant.DISABLE.equals(status)) {
            throw new DeletionNotAllowedException("默认管理员账号不能禁用");
        }
        if (employeeMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.EMPLOYEE_NOT_FOUND);
        }
        Employee employee = Employee.builder()
                .status(status)
                .id(id)
                .build();
        employeeMapper.update(employee);
        if (StatusConstant.DISABLE.equals(status)) {
            authSessionService.revokeAll(AuthSessionServiceImpl.EMPLOYEE, id, "ACCOUNT_DISABLED");
        }
    }

    /**
     * 根据id查询员工信息（密码脱敏）
     */
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) {
            throw new BaseException(MessageConstant.EMPLOYEE_NOT_FOUND);
        }
        employee.setPassword("****");
        return employee;
    }

    /**
     * 编辑员工信息
     */
    public void update(EmployeeDTO employeeDTO) {
        adminPermissionService.requireAdmin();
        if (employeeMapper.getById(employeeDTO.getId()) == null) {
            throw new BaseException(MessageConstant.EMPLOYEE_NOT_FOUND);
        }
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);
        employeeMapper.update(employee);
    }

    /**
     * 删除员工，保留默认管理员账号避免演示环境被锁死
     */
    @Transactional
    public void deleteById(Long id) {
        adminPermissionService.requireAdmin();
        if (Long.valueOf(1L).equals(id)) {
            throw new DeletionNotAllowedException("默认管理员账号不能删除");
        }
        if (employeeMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.EMPLOYEE_NOT_FOUND);
        }
        if (operationLogMapper.countByOperatorId(id) > 0) {
            throw new DeletionNotAllowedException(MessageConstant.EMPLOYEE_BE_RELATED_BY_OPERATION_LOG);
        }
        authSessionService.revokeAll(AuthSessionServiceImpl.EMPLOYEE, id, "ACCOUNT_DELETED");
        employeeMapper.deleteById(id);
    }

}
