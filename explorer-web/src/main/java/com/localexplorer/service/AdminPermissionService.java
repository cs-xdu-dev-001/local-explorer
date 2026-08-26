package com.localexplorer.service;

import com.localexplorer.context.BaseContext;
import com.localexplorer.entity.Employee;
import com.localexplorer.exception.ForbiddenOperationException;
import com.localexplorer.mapper.EmployeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdminPermissionService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_STAFF = "STAFF";
    public static final String FORBIDDEN_MESSAGE = "当前员工没有权限执行该操作";

    private final EmployeeMapper employeeMapper;

    @Autowired
    public AdminPermissionService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new ForbiddenOperationException(FORBIDDEN_MESSAGE);
        }
    }

    public boolean isAdmin() {
        Employee employee = currentEmployee();
        return employee != null && ROLE_ADMIN.equals(employee.getRole());
    }

    public Employee currentEmployee() {
        Long employeeId = BaseContext.getCurrentId();
        return employeeId == null ? null : employeeMapper.getById(employeeId);
    }
}
