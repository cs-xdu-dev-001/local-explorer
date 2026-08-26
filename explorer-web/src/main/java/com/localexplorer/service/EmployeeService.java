package com.localexplorer.service;

import com.localexplorer.dto.EmployeeDTO;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.dto.EmployeePageQueryDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.result.PageResult;

public interface EmployeeService {
    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);

    /**
     * 删除员工
     * @param id
     */
    void deleteById(Long id);
}
